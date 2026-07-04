# -*- coding: utf-8 -*-
"""
╔══════════════════════════════════════════════════════════════════════════════╗
║  GERADOR DE LICENÇAS — PDV Quantum Ultra / Quantum_Farma2.py                   ║
║                                                                                ║
║  Ferramenta do FABRICANTE para gerar a "Chave de Ativação" a partir do         ║
║  HWID e da quantidade de dias enviados pelo cliente.                           ║
║                                                                                ║
║  ⚠️  USO INTERNO / CONFIDENCIAL                                                 ║
║  Este arquivo contém o segredo do fabricante (_MASTER_SALT / _SECRET).         ║
║  NÃO distribua para clientes nem publique em repositório público.              ║
║                                                                                ║
║  O algoritmo abaixo é IDÊNTICO ao do Quantum_Farma2.py, garantindo que a       ║
║  chave gerada aqui seja aceita pela tela de ativação do sistema.               ║
╚══════════════════════════════════════════════════════════════════════════════╝

Modos de uso:
    1) Interface gráfica (padrão):   python GeradorLicenca.py
    2) Linha de comando:             python GeradorLicenca.py --hwid <HWID> --dias 30
    3) Autoteste do algoritmo:       python GeradorLicenca.py --selftest
"""

import argparse
import hashlib
import sys

# ═══════════════════════════════════════════════════════════════════════════════
# 🔐 SEGREDO DO FABRICANTE — deve ser EXATAMENTE igual ao do Quantum_Farma2.py
# ═══════════════════════════════════════════════════════════════════════════════
_MASTER_SALT = b"PDV_QUANTUM_ULTRA_2027_NEXUS_EDITION"
_LICENSE_VERSION = "4.0"
_SECRET = "NEXUS_PDV_2027"


# ═══════════════════════════════════════════════════════════════════════════════
# ALGORITMO (cópia fiel das funções do Quantum_Farma2.py)
# ═══════════════════════════════════════════════════════════════════════════════
def normalizar_hwid_licenca(hwid: str) -> str:
    return str(hwid or "").strip().replace("-", "").replace(" ", "").upper()


def normalizar_chave_licenca(chave: str) -> str:
    return str(chave or "").strip().replace("-", "").replace(" ", "").upper()


def formatar_chave_licenca(chave: str, bloco: int = 4) -> str:
    chave = normalizar_chave_licenca(chave)
    return "-".join(chave[i:i + bloco] for i in range(0, len(chave), bloco) if chave[i:i + bloco])


def calcular_contra_chave_licenca(hwid: str, dias: int) -> str:
    """Contra-chave que o cliente vê no sistema. Serve para conferir os dados."""
    hwid_normalizado = normalizar_hwid_licenca(hwid)
    contra_data = f"{hwid_normalizado}|{int(dias)}|{_LICENSE_VERSION}"
    contra_hash = hashlib.sha256(contra_data.encode()).hexdigest()
    contra_key = contra_hash[:12].upper()
    return f"{contra_key[:4]}-{contra_key[4:8]}-{contra_key[8:12]}"


def calcular_chave_ativacao_licenca(hwid: str, dias: int) -> str:
    """Chave de Ativação (16 hex maiúsculos) que o sistema espera receber."""
    hwid_normalizado = normalizar_hwid_licenca(hwid)
    expected_data = f"{hwid_normalizado}|{int(dias)}|{_MASTER_SALT.decode()}|{_SECRET}"
    expected_hash = hashlib.sha256(expected_data.encode()).hexdigest()
    return expected_hash[:16].upper()


def validar_chave_ativacao_licenca(hwid: str, dias: int, chave: str) -> bool:
    return normalizar_chave_licenca(chave) == calcular_chave_ativacao_licenca(hwid, int(dias))


# ═══════════════════════════════════════════════════════════════════════════════
# HELPERS
# ═══════════════════════════════════════════════════════════════════════════════
def gerar_licenca(hwid: str, dias: int) -> dict:
    """Retorna todos os dados úteis para o fabricante."""
    hwid_norm = normalizar_hwid_licenca(hwid)
    dias = int(dias)
    chave_raw = calcular_chave_ativacao_licenca(hwid_norm, dias)
    resultado = {
        "hwid": hwid_norm,
        "dias": dias,
        "contra_chave": calcular_contra_chave_licenca(hwid_norm, dias),
        "chave_ativacao": formatar_chave_licenca(chave_raw),  # XXXX-XXXX-XXXX-XXXX
        "chave_ativacao_raw": chave_raw,                       # 16 chars sem hífen
    }
    try:
        import datetime
        venc = datetime.date.today() + datetime.timedelta(days=dias)
        resultado["vencimento_estimado"] = venc.strftime("%d/%m/%Y")
    except Exception:
        resultado["vencimento_estimado"] = ""
    return resultado


def parse_dados_cliente(texto: str) -> dict:
    """Extrai HWID / DIAS / CONTRA-CHAVE do bloco colado pelo cliente.

    O botão "Copiar dados para o gerador" do sistema produz algo como:
        HWID: ABC123...
        DIAS: 30
        CONTRA-CHAVE: 1A2B-3C4D-5E6F
    """
    dados = {"hwid": "", "dias": "", "contra": ""}
    for linha in str(texto or "").splitlines():
        if ":" not in linha:
            continue
        rotulo, _, valor = linha.partition(":")
        rotulo = rotulo.strip().upper().replace("-", "").replace(" ", "")
        valor = valor.strip()
        if rotulo == "HWID":
            dados["hwid"] = valor
        elif rotulo == "DIAS":
            dados["dias"] = valor
        elif rotulo in ("CONTRACHAVE", "CONTRA"):
            dados["contra"] = valor
    return dados


# ═══════════════════════════════════════════════════════════════════════════════
# AUTOTESTE — garante compatibilidade com a validação do sistema
# ═══════════════════════════════════════════════════════════════════════════════
def _selftest() -> int:
    casos = [
        ("A1B2-C3D4-E5F6", 30),
        ("abcdef0123456789ABCDEF", 90),
        ("  HW ID-COM-HIFENS-e-espacos  ", 365),
        ("QUANTUM_FALLBACK", 1),
    ]
    ok = True
    print("=== AUTOTESTE DO GERADOR DE LICENÇA ===")
    for hwid, dias in casos:
        info = gerar_licenca(hwid, dias)
        # A chave gerada deve ser aceita pela mesma função de validação do sistema.
        valida = validar_chave_ativacao_licenca(hwid, dias, info["chave_ativacao"])
        valida_raw = validar_chave_ativacao_licenca(hwid, dias, info["chave_ativacao_raw"])
        # Dias diferentes NÃO podem validar (chave é vinculada aos dias).
        errada = validar_chave_ativacao_licenca(hwid, dias + 1, info["chave_ativacao"])
        status = "OK" if (valida and valida_raw and not errada) else "FALHOU"
        if status != "OK":
            ok = False
        print(f"[{status}] dias={dias:<4} hwid={normalizar_hwid_licenca(hwid)[:16]}... "
              f"chave={info['chave_ativacao']} contra={info['contra_chave']}")
    print("=======================================")
    print("RESULTADO:", "TODOS OS TESTES PASSARAM ✅" if ok else "HÁ FALHAS ❌")
    return 0 if ok else 1


# ═══════════════════════════════════════════════════════════════════════════════
# CLI
# ═══════════════════════════════════════════════════════════════════════════════
def _run_cli(args) -> int:
    if not args.hwid:
        print("ERRO: informe --hwid (ou rode sem argumentos para abrir a interface gráfica).")
        return 2
    info = gerar_licenca(args.hwid, args.dias)
    print("HWID................:", info["hwid"])
    print("Dias...............:", info["dias"])
    print("Vencimento estimado:", info["vencimento_estimado"])
    print("Contra-Chave.......:", info["contra_chave"])
    print("CHAVE DE ATIVAÇÃO..:", info["chave_ativacao"])

    if args.contra:
        esperada = calcular_contra_chave_licenca(args.hwid, args.dias)
        if normalizar_chave_licenca(args.contra) == normalizar_chave_licenca(esperada):
            print("Conferência contra-chave: OK ✅ (dados do cliente conferem)")
        else:
            print("Conferência contra-chave: DIVERGENTE ❌ (HWID ou dias podem estar errados!)")
    return 0


# ═══════════════════════════════════════════════════════════════════════════════
# INTERFACE GRÁFICA (Tkinter)
# ═══════════════════════════════════════════════════════════════════════════════
def _run_gui() -> int:
    try:
        import tkinter as tk
        from tkinter import ttk, messagebox
    except Exception as e:
        print("Não foi possível abrir a interface gráfica (Tkinter indisponível):", e)
        print("Use o modo linha de comando: python GeradorLicenca.py --hwid <HWID> --dias 30")
        return 1

    root = tk.Tk()
    root.title("Gerador de Licenças — PDV Quantum Ultra")
    root.geometry("760x640")
    root.minsize(680, 600)

    style = ttk.Style()
    try:
        style.theme_use("clam")
    except Exception:
        pass

    main = ttk.Frame(root, padding=16)
    main.pack(fill=tk.BOTH, expand=True)

    ttk.Label(main, text="🔑 Gerador de Licenças", font=("Segoe UI", 17, "bold")).pack(anchor=tk.W)
    ttk.Label(
        main,
        text="Cole os dados enviados pelo cliente (ou preencha manualmente) e gere a Chave de Ativação.",
        font=("Segoe UI", 10), wraplength=720,
    ).pack(anchor=tk.W, pady=(2, 12))

    # ── Bloco colado pelo cliente ─────────────────────────────────────────────
    colar_frame = ttk.LabelFrame(main, text="1) Dados recebidos do cliente (opcional: colar e preencher)", padding=10)
    colar_frame.pack(fill=tk.X)
    txt_colar = tk.Text(colar_frame, height=4, font=("Consolas", 9), wrap="word")
    txt_colar.pack(fill=tk.X, side=tk.LEFT, expand=True)

    # ── Campos ─────────────────────────────────────────────────────────────────
    campos = ttk.LabelFrame(main, text="2) Dados da licença", padding=12)
    campos.pack(fill=tk.X, pady=12)
    campos.columnconfigure(1, weight=1)

    hwid_var = tk.StringVar()
    dias_var = tk.StringVar(value="30")
    contra_cliente_var = tk.StringVar()

    ttk.Label(campos, text="HWID do cliente:").grid(row=0, column=0, sticky=tk.W, padx=5, pady=6)
    hwid_entry = ttk.Entry(campos, textvariable=hwid_var, font=("Consolas", 9))
    hwid_entry.grid(row=0, column=1, sticky=tk.EW, padx=5, pady=6)

    ttk.Label(campos, text="Dias da licença:").grid(row=1, column=0, sticky=tk.W, padx=5, pady=6)
    dias_frame = ttk.Frame(campos)
    dias_frame.grid(row=1, column=1, sticky=tk.W, padx=5, pady=6)
    ttk.Entry(dias_frame, textvariable=dias_var, width=10).pack(side=tk.LEFT)
    for d in (30, 90, 180, 365):
        ttk.Button(dias_frame, text=f"{d}d", width=5, command=lambda x=d: dias_var.set(str(x))).pack(side=tk.LEFT, padx=2)

    ttk.Label(campos, text="Contra-Chave do cliente:").grid(row=2, column=0, sticky=tk.W, padx=5, pady=6)
    ttk.Entry(campos, textvariable=contra_cliente_var, font=("Consolas", 11)).grid(row=2, column=1, sticky=tk.W, padx=5, pady=6)
    ttk.Label(campos, text="(opcional — usada só para conferir se os dados batem)",
              font=("Segoe UI", 8)).grid(row=3, column=1, sticky=tk.W, padx=5)

    def preencher_do_texto():
        d = parse_dados_cliente(txt_colar.get("1.0", tk.END))
        if d["hwid"]:
            hwid_var.set(d["hwid"])
        if d["dias"]:
            dias_var.set(d["dias"])
        if d["contra"]:
            contra_cliente_var.set(d["contra"])
        if not any(d.values()):
            messagebox.showwarning("Nada encontrado",
                                   "Não encontrei HWID/DIAS/CONTRA-CHAVE no texto colado.", parent=root)

    ttk.Button(colar_frame, text="⬇ Preencher\nautomaticamente", command=preencher_do_texto).pack(side=tk.LEFT, padx=8)

    # ── Resultado ────────────────────────────────────────────────────────────
    resultado = ttk.LabelFrame(main, text="3) Resultado", padding=12)
    resultado.pack(fill=tk.BOTH, expand=True)
    resultado.columnconfigure(1, weight=1)

    chave_var = tk.StringVar()
    contra_calc_var = tk.StringVar()
    venc_var = tk.StringVar()
    confer_var = tk.StringVar()

    ttk.Label(resultado, text="CHAVE DE ATIVAÇÃO:", font=("Segoe UI", 10, "bold")).grid(row=0, column=0, sticky=tk.W, padx=5, pady=8)
    chave_entry = ttk.Entry(resultado, textvariable=chave_var, font=("Consolas", 15, "bold"), state="readonly")
    chave_entry.grid(row=0, column=1, sticky=tk.EW, padx=5, pady=8)

    ttk.Label(resultado, text="Contra-Chave calculada:").grid(row=1, column=0, sticky=tk.W, padx=5, pady=4)
    ttk.Entry(resultado, textvariable=contra_calc_var, font=("Consolas", 11), state="readonly").grid(row=1, column=1, sticky=tk.W, padx=5, pady=4)

    ttk.Label(resultado, text="Vencimento estimado:").grid(row=2, column=0, sticky=tk.W, padx=5, pady=4)
    ttk.Label(resultado, textvariable=venc_var, font=("Segoe UI", 10)).grid(row=2, column=1, sticky=tk.W, padx=5, pady=4)

    ttk.Label(resultado, textvariable=confer_var, font=("Segoe UI", 10, "bold"), wraplength=680).grid(
        row=3, column=0, columnspan=2, sticky=tk.W, padx=5, pady=(8, 0))

    def gerar():
        hwid = hwid_var.get().strip()
        if not hwid:
            messagebox.showerror("HWID obrigatório", "Informe o HWID enviado pelo cliente.", parent=root)
            return
        try:
            dias = int(str(dias_var.get()).strip())
            if dias <= 0:
                raise ValueError
        except Exception:
            messagebox.showerror("Dias inválido", "Informe uma quantidade de dias maior que zero.", parent=root)
            return

        info = gerar_licenca(hwid, dias)
        chave_var.set(info["chave_ativacao"])
        contra_calc_var.set(info["contra_chave"])
        venc_var.set(info["vencimento_estimado"])

        contra_cli = contra_cliente_var.get().strip()
        if contra_cli:
            if normalizar_chave_licenca(contra_cli) == normalizar_chave_licenca(info["contra_chave"]):
                confer_var.set("✅ Contra-chave do cliente confere — HWID e dias estão corretos.")
            else:
                confer_var.set("❌ Contra-chave DIVERGENTE! Confira se o HWID e os dias foram digitados exatamente como o cliente enviou.")
        else:
            confer_var.set("ℹ️ Contra-chave do cliente não informada (conferência não realizada).")

    def copiar_chave():
        if not chave_var.get():
            return
        root.clipboard_clear()
        root.clipboard_append(chave_var.get())
        messagebox.showinfo("Copiado", "Chave de Ativação copiada para a área de transferência.", parent=root)

    botoes = ttk.Frame(main)
    botoes.pack(fill=tk.X, pady=(12, 0))
    ttk.Button(botoes, text="⚙  Gerar Chave de Ativação", command=gerar).pack(side=tk.LEFT)
    ttk.Button(botoes, text="📋 Copiar chave", command=copiar_chave).pack(side=tk.LEFT, padx=8)
    ttk.Button(botoes, text="Sair", command=root.destroy).pack(side=tk.RIGHT)

    root.bind("<Return>", lambda e: gerar())
    hwid_entry.focus_set()
    root.mainloop()
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Gerador de licenças do PDV Quantum Ultra / Quantum_Farma2.py")
    parser.add_argument("--hwid", help="HWID enviado pelo cliente")
    parser.add_argument("--dias", type=int, default=30, help="Quantidade de dias da licença (padrão: 30)")
    parser.add_argument("--contra", help="Contra-chave enviada pelo cliente (opcional, para conferência)")
    parser.add_argument("--selftest", action="store_true", help="Executa o autoteste do algoritmo e sai")
    parser.add_argument("--cli", action="store_true", help="Força modo linha de comando")
    args = parser.parse_args()

    if args.selftest:
        return _selftest()
    if args.cli or args.hwid:
        return _run_cli(args)
    return _run_gui()


if __name__ == "__main__":
    sys.exit(main())
