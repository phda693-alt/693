# -*- coding: utf-8 -*-
"""
================================================================================
PDV Pro - Servidor de Impressao (.fr3 / FastReport)
================================================================================
Servidor HTTP local que recebe cupons do PDV Pro e:
  1. Preenche um modelo FastReport (.fr3) com o conteudo do cupom;
  2. Salva o .fr3 pronto numa pasta de saida;
  3. (Opcional) imprime automaticamente:
       - via um "runner" do FastReport (ex.: um .exe que abre e imprime o .fr3);
       - ou, como alternativa, imprime o texto cru direto na impressora
         (win32print RAW no Windows / lp no Linux).

Como usar:
  1. Coloque este arquivo e o "cupom.fr3" na mesma pasta.
  2. Execute:  python print_server.py
  3. No PDV Pro, em Configuracoes de Impressora, marque
     "Enviar impressao para o Servidor de Impressao (.fr3)" e informe a URL
     (padrao: http://127.0.0.1:8899/print).

Configuracao: editada em "print_server_config.json" (criado automaticamente).

Compativel com Python 3.7+ (somente biblioteca padrao).
================================================================================
"""

import os
import sys
import json
import time
import threading
import datetime
import subprocess
import platform
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

APP_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(APP_DIR, "print_server_config.json")
TEMPLATE_FILE = os.path.join(APP_DIR, "cupom.fr3")
LOG_FILE = os.path.join(APP_DIR, "print_server.log")

# ---------------------------------------------------------------------------
# Configuracao padrao ("arrojada": tudo configuravel)
# ---------------------------------------------------------------------------
DEFAULT_CONFIG = {
    "host": "0.0.0.0",                 # 0.0.0.0 aceita conexoes de outros PCs da rede
    "port": 8899,
    "pasta_saida": os.path.join(APP_DIR, "cupons_fr3"),
    "modelo_fr3": TEMPLATE_FILE,        # modelo .fr3 usado como base
    "titulo_padrao": "CUPOM NAO FISCAL",
    "manter_arquivos": True,            # manter os .fr3 gerados
    "max_arquivos": 500,                # limpa os mais antigos alem deste limite
    "impressao_automatica": False,      # imprimir automaticamente ao receber
    "fastreport_runner": "",            # ex.: C:/FR/FRPrint.exe  (recebe o .fr3)
    "fastreport_args": ["{fr3}", "/print", "/silent"],
    "impressao_raw_fallback": True,     # se nao houver runner, imprime texto cru
    "nome_impressora": "",              # impressora p/ o fallback RAW (vazio = padrao)
    "corte_automatico": True,           # cortar o papel ao final da impressao
    "corte_tipo": "parcial",            # "parcial" (com avanco) ou "total"
    "avanco_linhas_corte": 4,           # linhas em branco antes do corte
    "log_verboso": True,
}


def carregar_config():
    cfg = dict(DEFAULT_CONFIG)
    try:
        if os.path.exists(CONFIG_FILE):
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                saved = json.load(f)
            for k, v in saved.items():
                cfg[k] = v
    except Exception as e:
        log(f"Erro ao ler config, usando padrao: {e}")
    # Garante que o arquivo exista/atualize com novas chaves
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2, ensure_ascii=False)
    except Exception as e:
        log(f"Erro ao gravar config: {e}")
    return cfg


def log(msg):
    linha = f"[{datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {msg}"
    print(linha, flush=True)
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(linha + "\n")
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Modelo .fr3 embutido (usado se "cupom.fr3" nao existir na pasta)
# Contem os marcadores [[TITULO]], [[CONTEUDO]] e [[DATAHORA]] que sao
# substituidos pelo servidor a cada cupom.
# ---------------------------------------------------------------------------
FR3_EMBUTIDO = r'''<?xml version="1.0" encoding="utf-8"?>
<TfrxReport Version="6.9.13" DotMatrix="0" IniFile="\Software\Fast Reports"
  PreviewOptions.Buttons="15" PrintOptions.Printer="Default"
  ReportOptions.Name="CupomPDVPro" ScriptLanguage="PascalScript"
  ScriptText.Text="begin&#13;&#10;end." Left="0" Top="0" Width="0" Height="0">
  <TfrxDataPage Name="Data" Height="1000.000000000000000000" Left="0.000000000000000000"
    Top="0.000000000000000000" Width="1000.000000000000000000"/>
  <TfrxReportPage Name="Page1" PaperWidth="72" PaperHeight="500" PaperSize="256"
    LeftMargin="2" RightMargin="2" TopMargin="2" BottomMargin="2" Columns="0"
    ColumnWidth="0.000000000000000000" ColumnPositions.Text="">
    <TfrxReportTitle Name="ReportTitle1" Height="56.692950000000000000"
      Top="18.897650000000000000" Width="264.567100000000000000">
      <TfrxMemoView Name="MemoTitulo" AllowVectorExport="1" Left="0"
        Top="0.000000000000000000" Width="264.567100000000000000"
        Height="56.692950000000000000" HAlign="haCenter" VAlign="vaCenter"
        Font.Charset="0" Font.Color="0" Font.Height="-24" Font.Name="Courier New"
        Font.Style="1" Memo.Text="[[TITULO]]"/>
    </TfrxReportTitle>
    <TfrxMasterData Name="MasterData1" Height="18.897650000000000000"
      Top="94.488250000000000000" Width="264.567100000000000000" RowCount="1">
      <TfrxMemoView Name="MemoConteudo" AllowVectorExport="1" Left="0"
        Top="0.000000000000000000" Width="264.567100000000000000"
        Height="18.897650000000000000" Font.Charset="0" Font.Color="0"
        Font.Height="-16" Font.Name="Courier New" Font.Style="1"
        StretchMode="smActualHeight" WordWrap="0" Memo.Text="[[CONTEUDO]]"/>
    </TfrxMasterData>
    <TfrxReportSummary Name="ReportSummary1" Height="30.236240000000000000"
      Top="132.283550000000000000" Width="264.567100000000000000">
      <TfrxMemoView Name="MemoRodape" AllowVectorExport="1" Left="0"
        Top="0.000000000000000000" Width="264.567100000000000000"
        Height="30.236240000000000000" HAlign="haCenter" Font.Charset="0"
        Font.Color="0" Font.Height="-13" Font.Name="Courier New" Font.Style="0"
        Memo.Text="[[DATAHORA]]"/>
    </TfrxReportSummary>
  </TfrxReportPage>
</TfrxReport>
'''


def escapar_xml(txt):
    """Escapa caracteres especiais e converte quebras de linha para o formato
    aceito dentro de um atributo .fr3 (CRLF => &#13;&#10;)."""
    if txt is None:
        txt = ""
    txt = (txt.replace("&", "&amp;")
              .replace("<", "&lt;")
              .replace(">", "&gt;")
              .replace('"', "&quot;"))
    txt = txt.replace("\r\n", "\n").replace("\r", "\n")
    txt = txt.replace("\n", "&#13;&#10;")
    return txt


def obter_modelo(cfg):
    """Retorna o conteudo do modelo .fr3 (do arquivo ou o embutido)."""
    caminho = cfg.get("modelo_fr3") or TEMPLATE_FILE
    try:
        if os.path.exists(caminho):
            with open(caminho, "r", encoding="utf-8") as f:
                return f.read()
    except Exception as e:
        log(f"Falha ao ler modelo {caminho}: {e}")
    # cria o modelo padrao para o usuario poder editar depois
    try:
        with open(caminho, "w", encoding="utf-8") as f:
            f.write(FR3_EMBUTIDO)
        log(f"Modelo .fr3 padrao criado em: {caminho}")
    except Exception:
        pass
    return FR3_EMBUTIDO


def gerar_fr3(cfg, texto, titulo, data_hora):
    """Preenche o modelo .fr3 com os dados do cupom e salva na pasta de saida.
    Retorna o caminho do arquivo gerado."""
    modelo = obter_modelo(cfg)
    conteudo = (modelo
                .replace("[[TITULO]]", escapar_xml(titulo))
                .replace("[[CONTEUDO]]", escapar_xml(texto))
                .replace("[[DATAHORA]]", escapar_xml(data_hora)))
    pasta = cfg.get("pasta_saida") or os.path.join(APP_DIR, "cupons_fr3")
    os.makedirs(pasta, exist_ok=True)
    nome = f"cupom_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S_%f')}.fr3"
    caminho = os.path.join(pasta, nome)
    with open(caminho, "w", encoding="utf-8") as f:
        f.write(conteudo)
    return caminho


def limpar_antigos(cfg):
    """Mantem apenas os N arquivos mais recentes na pasta de saida."""
    try:
        maximo = int(cfg.get("max_arquivos", 500) or 500)
        pasta = cfg.get("pasta_saida")
        if not pasta or not os.path.isdir(pasta):
            return
        arquivos = [os.path.join(pasta, x) for x in os.listdir(pasta)
                    if x.lower().endswith(".fr3")]
        if len(arquivos) <= maximo:
            return
        arquivos.sort(key=lambda p: os.path.getmtime(p))
        for p in arquivos[:len(arquivos) - maximo]:
            try:
                os.remove(p)
            except Exception:
                pass
    except Exception as e:
        log(f"Erro na limpeza de arquivos: {e}")


def imprimir_fr3(cfg, fr3_path, texto):
    """Tenta imprimir o cupom. Retorna (sucesso, mensagem)."""
    if not cfg.get("impressao_automatica"):
        return True, "Arquivo .fr3 gerado (impressao automatica desligada)."

    # 1) Runner do FastReport (imprime o proprio .fr3, layout completo)
    runner = (cfg.get("fastreport_runner") or "").strip()
    if runner and os.path.exists(runner):
        try:
            args = [runner] + [a.replace("{fr3}", fr3_path)
                               for a in cfg.get("fastreport_args", ["{fr3}"])]
            subprocess.run(args, timeout=60)
            return True, f"Impresso via FastReport runner: {os.path.basename(runner)}"
        except Exception as e:
            log(f"Runner FastReport falhou: {e}")

    # 2) Fallback: imprime o TEXTO cru direto na impressora
    if cfg.get("impressao_raw_fallback", True):
        return _imprimir_raw(cfg, texto)

    return True, "Arquivo .fr3 gerado (sem runner configurado)."


def _comando_corte(cfg):
    """Retorna os bytes ESC/POS de avanco + corte do papel, conforme config."""
    if not cfg.get("corte_automatico", True):
        return b""
    try:
        avanco = int(cfg.get("avanco_linhas_corte", 4) or 0)
    except Exception:
        avanco = 4
    dados = b"\n" * max(0, avanco)
    if str(cfg.get("corte_tipo", "parcial")).lower().startswith("t"):
        dados += b"\x1d\x56\x00"       # GS V 0  = corte TOTAL
    else:
        dados += b"\x1d\x56\x42\x00"   # GS V 66 0 = corte PARCIAL (com avanco)
    return dados


def _imprimir_raw(cfg, texto):
    sistema = platform.system()
    nome_impressora = (cfg.get("nome_impressora") or "").strip()
    # Texto + avanco/corte do papel ao final
    dados = texto.encode("utf-8", errors="replace") + _comando_corte(cfg)
    try:
        if sistema == "Windows":
            import win32print  # requer pywin32
            printer = nome_impressora or win32print.GetDefaultPrinter()
            h = win32print.OpenPrinter(printer)
            try:
                win32print.StartDocPrinter(h, 1, ("PDV Pro Cupom", None, "RAW"))
                win32print.StartPagePrinter(h)
                win32print.WritePrinter(h, dados)
                win32print.EndPagePrinter(h)
                win32print.EndDocPrinter(h)
            finally:
                win32print.ClosePrinter(h)
            return True, f"Impresso (RAW) em: {printer}"
        else:
            # Linux / macOS
            cmd = ["lp"]
            if nome_impressora:
                cmd += ["-d", nome_impressora]
            p = subprocess.run(cmd, input=dados, timeout=30)
            if p.returncode == 0:
                return True, "Impresso via lp"
            return False, f"lp retornou codigo {p.returncode}"
    except Exception as e:
        return False, f"Falha na impressao RAW: {e}"


class Handler(BaseHTTPRequestHandler):
    server_cfg = DEFAULT_CONFIG

    def _responder(self, codigo, payload):
        corpo = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)

    def log_message(self, fmt, *args):
        if self.server_cfg.get("log_verboso"):
            log("HTTP " + (fmt % args))

    def do_GET(self):
        # Health-check / status
        self._responder(200, {
            "ok": True,
            "servico": "PDV Pro - Servidor de Impressao .fr3",
            "status": "online",
            "hora": datetime.datetime.now().strftime("%d/%m/%Y %H:%M:%S"),
        })

    def do_POST(self):
        if not self.path.rstrip("/").endswith("/print") and self.path != "/print":
            self._responder(404, {"ok": False, "erro": "Rota nao encontrada. Use POST /print"})
            return
        try:
            tamanho = int(self.headers.get("Content-Length", 0))
            bruto = self.rfile.read(tamanho) if tamanho > 0 else b""
            dados = json.loads(bruto.decode("utf-8", errors="replace") or "{}")
        except Exception as e:
            self._responder(400, {"ok": False, "erro": f"JSON invalido: {e}"})
            return

        texto = dados.get("texto", "") or ""
        titulo = dados.get("titulo") or self.server_cfg.get("titulo_padrao", "CUPOM")
        copias = int(dados.get("copias", 1) or 1)
        data_hora = dados.get("data_hora") or datetime.datetime.now().strftime("%d/%m/%Y %H:%M:%S")

        if not texto.strip():
            self._responder(400, {"ok": False, "erro": "Campo 'texto' vazio."})
            return

        try:
            fr3_path = gerar_fr3(self.server_cfg, texto, titulo, data_hora)
            log(f"Cupom recebido ({len(texto)} chars) -> {os.path.basename(fr3_path)}")
            msgs = []
            sucesso_imp = True
            for _ in range(max(1, copias)):
                ok, msg = imprimir_fr3(self.server_cfg, fr3_path, texto)
                msgs.append(msg)
                sucesso_imp = sucesso_imp and ok
            limpar_antigos(self.server_cfg)
            self._responder(200, {
                "ok": True,
                "arquivo_fr3": fr3_path,
                "impressao_ok": sucesso_imp,
                "mensagem": " | ".join(msgs),
            })
        except Exception as e:
            log(f"ERRO ao processar cupom: {e}")
            self._responder(500, {"ok": False, "erro": str(e)})


def main():
    cfg = carregar_config()
    Handler.server_cfg = cfg
    # garante que o modelo exista
    obter_modelo(cfg)
    os.makedirs(cfg.get("pasta_saida", os.path.join(APP_DIR, "cupons_fr3")), exist_ok=True)

    host = cfg.get("host", "0.0.0.0")
    port = int(cfg.get("port", 8899))
    servidor = ThreadingHTTPServer((host, port), Handler)

    log("=" * 60)
    log("PDV Pro - Servidor de Impressao .fr3 iniciado")
    log(f"Escutando em http://{host}:{port}/print")
    log(f"Pasta de saida: {cfg.get('pasta_saida')}")
    log(f"Modelo .fr3:    {cfg.get('modelo_fr3')}")
    log(f"Impressao automatica: {cfg.get('impressao_automatica')}")
    log("Pressione CTRL+C para encerrar.")
    log("=" * 60)
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        log("Encerrando servidor...")
    finally:
        servidor.shutdown()


if __name__ == "__main__":
    main()
