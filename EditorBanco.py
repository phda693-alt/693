# -*- coding: utf-8 -*-
"""
EditorBanco.py
--------------
Editor manual de banco de dados MySQL com interface grafica (Tkinter).

Fluxo do programa:
  1) Tela de SENHA de acesso (senha = 4872) com caracteres mascarados (*).
  2) Tela de CONEXAO: usuario, senha e host do MySQL. Salva em config.json.
     Testa a conexao; se OK avanca.
  3) Tela MAXIMIZADA para ESCOLHER O BANCO de dados.
     Ao escolher, faz um BACKUP obrigatorio do banco antes de abrir.
  4) Tela de EDICAO: lista todas as tabelas (esquerda). Ao clicar numa
     tabela, mostra as colunas/linhas (direita). E possivel alterar
     manualmente qualquer valor (duplo clique na celula) e clicar em
     SALVAR para gravar as alteracoes no MySQL.

Requisitos no computador onde vai rodar:
  - Python 3
  - pip install mysql-connector-python
  - MySQL instalado e em execucao
  - (Opcional, para backup completo) mysqldump no PATH
"""

import os
import json
import datetime
import subprocess
import tkinter as tk
from tkinter import ttk, messagebox, filedialog, simpledialog

try:
    import mysql.connector
    from mysql.connector import Error as MySQLError
    MYSQL_DISPONIVEL = True
except Exception:
    MYSQL_DISPONIVEL = False
    MySQLError = Exception


# ------------------------------------------------------------------ #
# Constantes                                                         #
# ------------------------------------------------------------------ #
SENHA_ACESSO = "4872"
ARQ_CONFIG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.json")


# ------------------------------------------------------------------ #
# Aplicacao principal                                                #
# ------------------------------------------------------------------ #
class EditorBancoApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Editor de Banco de Dados MySQL")
        self.root.geometry("520x360")
        self._centralizar(self.root, 520, 360)

        # Estado da conexao
        self.conn = None
        self.config = {"host": "localhost", "user": "root", "password": ""}
        self.banco_atual = None

        # Estado da edicao de tabela
        self.tabela_atual = None
        self.colunas_atual = []          # nomes das colunas
        self.pk_atual = None             # nome da coluna chave primaria
        self.linhas_originais = {}       # item_id -> tuple original de valores
        self.alteracoes = {}             # item_id -> {coluna: novo_valor}
        self.dados_completos = []        # todas as linhas (para busca/filtro)
        self.ent_busca = None            # campo de busca
        self.combo_coluna = None         # combobox de coluna para busca

        self._carregar_config()
        self.tela_senha()

    # -------------------------------------------------------------- #
    # Utilitarios                                                    #
    # -------------------------------------------------------------- #
    @staticmethod
    def _centralizar(win, largura, altura):
        win.update_idletasks()
        sw = win.winfo_screenwidth()
        sh = win.winfo_screenheight()
        x = (sw - largura) // 2
        y = (sh - altura) // 2
        win.geometry(f"{largura}x{altura}+{x}+{y}")

    def _limpar_tela(self):
        for w in self.root.winfo_children():
            w.destroy()

    def _carregar_config(self):
        try:
            if os.path.exists(ARQ_CONFIG):
                with open(ARQ_CONFIG, "r", encoding="utf-8") as f:
                    dados = json.load(f)
                if isinstance(dados, dict):
                    self.config.update({
                        "host": dados.get("host", self.config["host"]),
                        "user": dados.get("user", self.config["user"]),
                        "password": dados.get("password", self.config["password"]),
                    })
        except Exception:
            pass

    def _salvar_config(self):
        try:
            with open(ARQ_CONFIG, "w", encoding="utf-8") as f:
                json.dump(self.config, f, indent=4, ensure_ascii=False)
        except Exception as e:
            messagebox.showwarning(
                "Aviso",
                f"Nao foi possivel salvar config.json:\n{e}")

    # ============================================================== #
    # TELA 1 - SENHA DE ACESSO                                       #
    # ============================================================== #
    def tela_senha(self):
        self._limpar_tela()
        self.root.geometry("520x360")
        self._centralizar(self.root, 520, 360)

        frame = tk.Frame(self.root, padx=30, pady=30)
        frame.pack(expand=True)

        tk.Label(frame, text="Acesso Restrito",
                 font=("Segoe UI", 18, "bold")).pack(pady=(0, 6))
        tk.Label(frame, text="Digite a senha de acesso para continuar",
                 font=("Segoe UI", 10)).pack(pady=(0, 20))

        entry = tk.Entry(frame, show="*", font=("Segoe UI", 14),
                         justify="center", width=18)
        entry.pack(pady=(0, 20), ipady=6)
        entry.focus_set()

        def verificar(event=None):
            if entry.get() == SENHA_ACESSO:
                self.tela_conexao()
            else:
                messagebox.showerror("Senha incorreta",
                                     "A senha digitada esta incorreta.")
                entry.delete(0, tk.END)
                entry.focus_set()

        tk.Button(frame, text="Entrar", font=("Segoe UI", 12, "bold"),
                  width=16, bg="#2d6cdf", fg="white", relief="flat",
                  cursor="hand2", command=verificar).pack()

        entry.bind("<Return>", verificar)

    # ============================================================== #
    # TELA 2 - CONEXAO MYSQL                                         #
    # ============================================================== #
    def tela_conexao(self):
        self._limpar_tela()
        self.root.geometry("520x460")
        self._centralizar(self.root, 520, 460)

        frame = tk.Frame(self.root, padx=30, pady=25)
        frame.pack(expand=True, fill="both")

        tk.Label(frame, text="Conexao com o MySQL",
                 font=("Segoe UI", 16, "bold")).pack(pady=(0, 4))
        tk.Label(frame, text="Informe os dados do MySQL instalado neste computador",
                 font=("Segoe UI", 9), fg="#555").pack(pady=(0, 20))

        def campo(label, valor, mostrar=None):
            tk.Label(frame, text=label, font=("Segoe UI", 10),
                     anchor="w").pack(fill="x")
            e = tk.Entry(frame, font=("Segoe UI", 12),
                         show=("*" if mostrar else ""))
            e.pack(fill="x", ipady=4, pady=(2, 12))
            e.insert(0, valor)
            return e

        e_host = campo("Host", self.config.get("host", "localhost"))
        e_user = campo("Usuario", self.config.get("user", "root"))
        e_pass = campo("Senha", self.config.get("password", ""), mostrar=True)

        status = tk.Label(frame, text="", font=("Segoe UI", 9), fg="red")
        status.pack(fill="x")

        def conectar():
            host = e_host.get().strip() or "localhost"
            user = e_user.get().strip() or "root"
            senha = e_pass.get()

            self.config["host"] = host
            self.config["user"] = user
            self.config["password"] = senha
            self._salvar_config()

            if not MYSQL_DISPONIVEL:
                messagebox.showerror(
                    "Modulo ausente",
                    "O modulo 'mysql-connector-python' nao esta instalado.\n\n"
                    "Instale com:\n    pip install mysql-connector-python")
                return

            status.config(text="Conectando...", fg="#555")
            self.root.update_idletasks()

            try:
                if self.conn:
                    try:
                        self.conn.close()
                    except Exception:
                        pass
                self.conn = mysql.connector.connect(
                    host=host, user=user, password=senha,
                    connection_timeout=8)
                if self.conn.is_connected():
                    status.config(text="Conectado com sucesso!", fg="green")
                    self.root.update_idletasks()
                    self.tela_selecionar_banco()
            except MySQLError as e:
                status.config(text="", fg="red")
                messagebox.showerror(
                    "Erro de conexao",
                    f"Nao foi possivel conectar ao MySQL:\n\n{e}")
            except Exception as e:
                status.config(text="", fg="red")
                messagebox.showerror("Erro", f"Erro inesperado:\n\n{e}")

        botoes = tk.Frame(frame)
        botoes.pack(fill="x", pady=(10, 0))
        tk.Button(botoes, text="Voltar", font=("Segoe UI", 11),
                  width=10, relief="flat", bg="#dddddd", cursor="hand2",
                  command=self.tela_senha).pack(side="left")
        tk.Button(botoes, text="Conectar", font=("Segoe UI", 11, "bold"),
                  width=14, bg="#2d6cdf", fg="white", relief="flat",
                  cursor="hand2", command=conectar).pack(side="right")

    # ============================================================== #
    # TELA 3 - SELECIONAR BANCO (MAXIMIZADA)                         #
    # ============================================================== #
    def _maximizar(self):
        try:
            self.root.state("zoomed")          # Windows
        except Exception:
            try:
                self.root.attributes("-zoomed", True)   # Linux
            except Exception:
                self.root.geometry(
                    f"{self.root.winfo_screenwidth()}x"
                    f"{self.root.winfo_screenheight()}+0+0")

    def tela_selecionar_banco(self):
        self._limpar_tela()
        self._maximizar()

        topo = tk.Frame(self.root, bg="#2d6cdf", height=60)
        topo.pack(fill="x")
        topo.pack_propagate(False)
        tk.Label(topo, text="Selecione o Banco de Dados",
                 font=("Segoe UI", 16, "bold"),
                 bg="#2d6cdf", fg="white").pack(side="left", padx=20)
        tk.Button(topo, text="Trocar conexao", font=("Segoe UI", 10),
                  relief="flat", bg="#1b4fa8", fg="white", cursor="hand2",
                  command=self.tela_conexao).pack(side="right", padx=20, pady=12)

        corpo = tk.Frame(self.root, padx=30, pady=20)
        corpo.pack(expand=True, fill="both")

        tk.Label(corpo,
                 text="Clique num banco para abri-lo. Sera feito um backup antes.",
                 font=("Segoe UI", 10), fg="#555").pack(anchor="w", pady=(0, 10))

        cont = tk.Frame(corpo)
        cont.pack(expand=True, fill="both")

        scroll = tk.Scrollbar(cont)
        scroll.pack(side="right", fill="y")

        lista = tk.Listbox(cont, font=("Segoe UI", 13),
                           yscrollcommand=scroll.set, activestyle="none")
        lista.pack(side="left", expand=True, fill="both")
        scroll.config(command=lista.yview)

        bancos = []
        try:
            cur = self.conn.cursor()
            cur.execute("SHOW DATABASES")
            for (nome,) in cur.fetchall():
                bancos.append(nome)
            cur.close()
        except Exception as e:
            messagebox.showerror("Erro",
                                 f"Nao foi possivel listar os bancos:\n{e}")

        # Ignorar bancos de sistema por padrao (mas mostra todos)
        for b in bancos:
            lista.insert(tk.END, b)

        def abrir_banco(event=None):
            sel = lista.curselection()
            if not sel:
                messagebox.showinfo("Atencao", "Selecione um banco na lista.")
                return
            banco = lista.get(sel[0])
            if self._fazer_backup(banco):
                self.banco_atual = banco
                try:
                    self.conn.database = banco
                except Exception as e:
                    messagebox.showerror("Erro",
                                         f"Nao foi possivel selecionar o banco:\n{e}")
                    return
                self.tela_edicao()

        lista.bind("<Double-Button-1>", abrir_banco)

        rodape = tk.Frame(corpo)
        rodape.pack(fill="x", pady=(15, 0))
        tk.Button(rodape, text="Abrir banco selecionado",
                  font=("Segoe UI", 12, "bold"), bg="#28a745", fg="white",
                  relief="flat", cursor="hand2", padx=20, pady=8,
                  command=abrir_banco).pack(side="right")

    # ============================================================== #
    # BACKUP                                                         #
    # ============================================================== #
    def _fazer_backup(self, banco):
        """Faz backup do banco antes de abrir. Retorna True se OK/confirmado."""
        pasta = filedialog.askdirectory(
            title=f"Escolha a pasta para salvar o backup do banco '{banco}'")
        if not pasta:
            resp = messagebox.askyesno(
                "Backup nao definido",
                "Voce nao escolheu uma pasta para o backup.\n\n"
                "Deseja abrir o banco MESMO ASSIM, sem backup?\n"
                "(NAO recomendado)")
            return bool(resp)

        ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        destino = os.path.join(pasta, f"backup_{banco}_{ts}.sql")

        # 1) Tenta mysqldump
        try:
            if self._backup_mysqldump(banco, destino):
                messagebox.showinfo(
                    "Backup concluido",
                    f"Backup criado com sucesso (mysqldump):\n\n{destino}")
                return True
        except Exception:
            pass

        # 2) Fallback: backup manual via queries
        try:
            self._backup_manual(banco, destino)
            messagebox.showinfo(
                "Backup concluido",
                f"Backup criado com sucesso (metodo manual):\n\n{destino}")
            return True
        except Exception as e:
            resp = messagebox.askyesno(
                "Falha no backup",
                f"Nao foi possivel criar o backup:\n{e}\n\n"
                "Deseja abrir o banco MESMO ASSIM, sem backup?\n"
                "(NAO recomendado)")
            return bool(resp)

    def _backup_mysqldump(self, banco, destino):
        cmd = [
            "mysqldump",
            f"--host={self.config['host']}",
            f"--user={self.config['user']}",
        ]
        senha = self.config.get("password", "")
        if senha:
            cmd.append(f"--password={senha}")
        cmd.append(banco)

        with open(destino, "w", encoding="utf-8") as fout:
            proc = subprocess.run(cmd, stdout=fout,
                                  stderr=subprocess.PIPE, timeout=300)
        if proc.returncode != 0:
            # Remove arquivo vazio/invalido
            try:
                if os.path.getsize(destino) == 0:
                    os.remove(destino)
            except Exception:
                pass
            raise RuntimeError(proc.stderr.decode("utf-8", "ignore"))
        return True

    def _backup_manual(self, banco, destino):
        cur = self.conn.cursor()
        cur.execute(f"USE `{banco}`")
        cur.execute("SHOW TABLES")
        tabelas = [r[0] for r in cur.fetchall()]

        linhas_sql = []
        linhas_sql.append(f"-- Backup manual do banco `{banco}`")
        linhas_sql.append(f"-- Gerado em {datetime.datetime.now()}")
        linhas_sql.append("SET FOREIGN_KEY_CHECKS=0;")
        linhas_sql.append("")

        for tab in tabelas:
            cur.execute(f"SHOW CREATE TABLE `{tab}`")
            row = cur.fetchone()
            create_sql = row[1] if row and len(row) > 1 else None
            if create_sql:
                linhas_sql.append(f"DROP TABLE IF EXISTS `{tab}`;")
                linhas_sql.append(create_sql + ";")
                linhas_sql.append("")

            cur.execute(f"SELECT * FROM `{tab}`")
            colunas = [d[0] for d in cur.description]
            dados = cur.fetchall()
            for reg in dados:
                vals = ", ".join(self._sql_valor(v) for v in reg)
                cols = ", ".join(f"`{c}`" for c in colunas)
                linhas_sql.append(
                    f"INSERT INTO `{tab}` ({cols}) VALUES ({vals});")
            linhas_sql.append("")

        linhas_sql.append("SET FOREIGN_KEY_CHECKS=1;")
        cur.close()

        with open(destino, "w", encoding="utf-8") as f:
            f.write("\n".join(linhas_sql))

    @staticmethod
    def _sql_valor(v):
        if v is None:
            return "NULL"
        if isinstance(v, (int, float)):
            return str(v)
        if isinstance(v, (bytes, bytearray)):
            return "0x" + v.hex()
        texto = str(v).replace("\\", "\\\\").replace("'", "\\'")
        return f"'{texto}'"

    # ============================================================== #
    # TELA 4 - EDICAO DE TABELAS                                     #
    # ============================================================== #
    def tela_edicao(self):
        self._limpar_tela()
        self._maximizar()

        # Barra superior
        topo = tk.Frame(self.root, bg="#2d6cdf", height=56)
        topo.pack(fill="x")
        topo.pack_propagate(False)
        tk.Label(topo, text=f"Banco: {self.banco_atual}",
                 font=("Segoe UI", 14, "bold"),
                 bg="#2d6cdf", fg="white").pack(side="left", padx=20)
        tk.Button(topo, text="Voltar aos bancos", font=("Segoe UI", 10),
                  relief="flat", bg="#1b4fa8", fg="white", cursor="hand2",
                  command=self.tela_selecionar_banco).pack(
                      side="right", padx=20, pady=11)
        tk.Button(topo, text="SQL Interativo", font=("Segoe UI", 10),
                  relief="flat", bg="#6f42c1", fg="white", cursor="hand2",
                  command=self.abrir_sql_interativo).pack(
                      side="right", padx=(0, 4), pady=11)
        tk.Button(topo, text="Restaurar", font=("Segoe UI", 10),
                  relief="flat", bg="#fd7e14", fg="white", cursor="hand2",
                  command=self.restaurar_banco).pack(
                      side="right", padx=(0, 4), pady=11)
        tk.Button(topo, text="Backup", font=("Segoe UI", 10),
                  relief="flat", bg="#17a2b8", fg="white", cursor="hand2",
                  command=self.backup_banco).pack(
                      side="right", padx=(0, 4), pady=11)

        # Corpo dividido: esquerda (tabelas) / direita (dados)
        painel = tk.PanedWindow(self.root, orient="horizontal",
                               sashwidth=6, bg="#cccccc")
        painel.pack(expand=True, fill="both")

        # --- Esquerda: lista de tabelas ---
        esq = tk.Frame(painel, bg="#f4f4f4")
        painel.add(esq, minsize=200, width=260)

        tk.Label(esq, text="Tabelas", font=("Segoe UI", 12, "bold"),
                 bg="#f4f4f4").pack(anchor="w", padx=12, pady=(12, 6))

        cont_lst = tk.Frame(esq)
        cont_lst.pack(expand=True, fill="both", padx=12, pady=(0, 12))
        sb = tk.Scrollbar(cont_lst)
        sb.pack(side="right", fill="y")
        self.lista_tabelas = tk.Listbox(
            cont_lst, font=("Segoe UI", 11),
            yscrollcommand=sb.set, activestyle="dotbox")
        self.lista_tabelas.pack(side="left", expand=True, fill="both")
        sb.config(command=self.lista_tabelas.yview)
        self.lista_tabelas.bind("<<ListboxSelect>>", self.carregar_tabela)

        # --- Direita: dados da tabela ---
        dir_ = tk.Frame(painel)
        painel.add(dir_, minsize=400)

        toolbar = tk.Frame(dir_, bg="#eeeeee", height=48)
        toolbar.pack(fill="x")
        toolbar.pack_propagate(False)

        self.lbl_tabela = tk.Label(toolbar, text="Selecione uma tabela",
                                   font=("Segoe UI", 12, "bold"), bg="#eeeeee")
        self.lbl_tabela.pack(side="left", padx=14)

        tk.Button(toolbar, text="Salvar alteracoes",
                  font=("Segoe UI", 11, "bold"), bg="#28a745", fg="white",
                  relief="flat", cursor="hand2", padx=14, pady=4,
                  command=self.salvar_alteracoes).pack(
                      side="right", padx=14, pady=7)
        tk.Button(toolbar, text="Limpar tabela",
                  font=("Segoe UI", 11, "bold"), bg="#dc3545", fg="white",
                  relief="flat", cursor="hand2", padx=12, pady=4,
                  command=self.limpar_tabela).pack(
                      side="right", padx=(0, 4), pady=7)
        tk.Button(toolbar, text="Clonar registro",
                  font=("Segoe UI", 11, "bold"), bg="#8e44ad", fg="white",
                  relief="flat", cursor="hand2", padx=12, pady=4,
                  command=self.clonar_registro).pack(
                      side="right", padx=(0, 4), pady=7)
        tk.Button(toolbar, text="Recarregar",
                  font=("Segoe UI", 11), bg="#dddddd",
                  relief="flat", cursor="hand2", padx=10, pady=4,
                  command=lambda: self._exibir_dados_tabela(self.tabela_atual)
                  ).pack(side="right", padx=(0, 4), pady=7)

        # Barra de busca
        barra_busca = tk.Frame(dir_, bg="#f7f7f7")
        barra_busca.pack(fill="x", padx=8, pady=(6, 0))
        tk.Label(barra_busca, text="Buscar:", font=("Segoe UI", 10),
                 bg="#f7f7f7").pack(side="left", padx=(6, 4))
        self.ent_busca = tk.Entry(barra_busca, font=("Segoe UI", 11), width=30)
        self.ent_busca.pack(side="left", ipady=2)
        self.ent_busca.bind("<Return>", lambda e: self._buscar())
        tk.Label(barra_busca, text="  na coluna:", font=("Segoe UI", 10),
                 bg="#f7f7f7").pack(side="left")
        self.combo_coluna = ttk.Combobox(barra_busca, font=("Segoe UI", 10),
                                          state="readonly", width=22)
        self.combo_coluna.pack(side="left", padx=4)
        self.combo_coluna["values"] = ["Todas as colunas"]
        self.combo_coluna.current(0)
        tk.Button(barra_busca, text="Buscar", font=("Segoe UI", 10),
                  bg="#2d6cdf", fg="white", relief="flat", cursor="hand2",
                  padx=12, pady=2, command=self._buscar).pack(
                      side="left", padx=4)
        tk.Button(barra_busca, text="Limpar filtro", font=("Segoe UI", 10),
                  bg="#dddddd", relief="flat", cursor="hand2",
                  padx=10, pady=2, command=self._limpar_filtro).pack(
                      side="left", padx=(0, 4))

        self.lbl_status = tk.Label(dir_, text="", font=("Segoe UI", 9),
                                   fg="#555", anchor="w")
        self.lbl_status.pack(fill="x", padx=14, pady=(4, 0))

        # Treeview + scrollbars
        cont_tree = tk.Frame(dir_)
        cont_tree.pack(expand=True, fill="both", padx=8, pady=8)

        vsb = ttk.Scrollbar(cont_tree, orient="vertical")
        hsb = ttk.Scrollbar(cont_tree, orient="horizontal")
        self.tree = ttk.Treeview(
            cont_tree, show="headings",
            yscrollcommand=vsb.set, xscrollcommand=hsb.set)
        vsb.config(command=self.tree.yview)
        hsb.config(command=self.tree.xview)

        self.tree.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns")
        hsb.grid(row=1, column=0, sticky="ew")
        cont_tree.rowconfigure(0, weight=1)
        cont_tree.columnconfigure(0, weight=1)

        self.tree.bind("<Double-1>", self._editar_celula)

        # Carregar nomes das tabelas
        self._carregar_lista_tabelas()

    def _carregar_lista_tabelas(self):
        self.lista_tabelas.delete(0, tk.END)
        try:
            cur = self.conn.cursor()
            cur.execute("SHOW TABLES")
            for (nome,) in cur.fetchall():
                self.lista_tabelas.insert(tk.END, nome)
            cur.close()
        except Exception as e:
            messagebox.showerror("Erro",
                                 f"Nao foi possivel listar as tabelas:\n{e}")

    def carregar_tabela(self, event=None):
        sel = self.lista_tabelas.curselection()
        if not sel:
            return
        tabela = self.lista_tabelas.get(sel[0])

        # Aviso se ha alteracoes pendentes
        if self.alteracoes and tabela != self.tabela_atual:
            if not messagebox.askyesno(
                    "Alteracoes pendentes",
                    "Ha alteracoes nao salvas na tabela atual.\n"
                    "Deseja descarta-las e abrir a nova tabela?"):
                return

        self.tabela_atual = tabela
        self._exibir_dados_tabela(tabela)

    def _exibir_dados_tabela(self, tabela):
        if not tabela:
            return
        self.lbl_tabela.config(text=f"Tabela: {tabela}")
        self.alteracoes = {}
        self.linhas_originais = {}
        self.dados_completos = []

        # Limpar treeview
        self.tree.delete(*self.tree.get_children())

        try:
            cur = self.conn.cursor()

            # Descobrir chave primaria
            self.pk_atual = None
            cur.execute(f"SHOW KEYS FROM `{tabela}` WHERE Key_name = 'PRIMARY'")
            pk = cur.fetchone()
            if pk:
                # Column_name costuma ser o indice 4
                self.pk_atual = pk[4]

            # Buscar dados
            cur.execute(f"SELECT * FROM `{tabela}`")
            colunas = [d[0] for d in cur.description]
            dados = cur.fetchall()
            cur.close()

            self.colunas_atual = colunas

            # Configurar colunas do treeview
            self.tree["columns"] = colunas
            for c in colunas:
                titulo = c + (" [PK]" if c == self.pk_atual else "")
                self.tree.heading(c, text=titulo)
                self.tree.column(c, width=140, minwidth=60, anchor="w")

            # Guardar todas as linhas (para busca/filtro)
            for reg in dados:
                valores = ["" if v is None else str(v) for v in reg]
                self.dados_completos.append(valores)

            # Atualizar combobox de colunas da busca
            if self.combo_coluna is not None:
                self.combo_coluna["values"] = ["Todas as colunas"] + colunas
                self.combo_coluna.current(0)
            if self.ent_busca is not None:
                self.ent_busca.delete(0, tk.END)

            # Renderizar linhas (sem filtro)
            self._render_linhas()
        except Exception as e:
            messagebox.showerror("Erro",
                                 f"Nao foi possivel carregar a tabela:\n{e}")

    def _render_linhas(self, filtro="", coluna=None):
        """Insere no treeview as linhas de dados_completos que casam com o filtro."""
        self.tree.delete(*self.tree.get_children())
        self.linhas_originais = {}
        self.alteracoes = {}

        filtro = (filtro or "").strip().lower()
        idx_coluna = None
        if coluna and coluna in self.colunas_atual:
            idx_coluna = self.colunas_atual.index(coluna)

        mostrados = 0
        for valores in self.dados_completos:
            if filtro:
                if idx_coluna is not None:
                    alvo = str(valores[idx_coluna]).lower()
                    if filtro not in alvo:
                        continue
                else:
                    if not any(filtro in str(v).lower() for v in valores):
                        continue
            item = self.tree.insert("", tk.END, values=valores)
            self.linhas_originais[item] = tuple(valores)
            mostrados += 1

        pk_txt = self.pk_atual if self.pk_atual else "NENHUMA (edicao desabilitada)"
        if filtro:
            alvo_txt = coluna if idx_coluna is not None else "todas as colunas"
            self.lbl_status.config(
                text=f"{mostrados} de {len(self.dados_completos)} registro(s) "
                     f"(filtro: '{filtro}' em {alvo_txt}) | Chave primaria: {pk_txt}")
        else:
            self.lbl_status.config(
                text=f"{mostrados} registro(s) | Chave primaria: {pk_txt} "
                     f"| Duplo clique numa celula para editar.")

    def _buscar(self):
        if not self.tabela_atual:
            return
        if self.alteracoes:
            if not messagebox.askyesno(
                    "Alteracoes pendentes",
                    "Ha alteracoes nao salvas. A busca vai descarta-las.\n"
                    "Deseja continuar?"):
                return
        termo = self.ent_busca.get() if self.ent_busca else ""
        col = self.combo_coluna.get() if self.combo_coluna else ""
        coluna = None if col in ("", "Todas as colunas") else col
        self._render_linhas(termo, coluna)

    def _limpar_filtro(self):
        if self.ent_busca is not None:
            self.ent_busca.delete(0, tk.END)
        if self.combo_coluna is not None and self.combo_coluna["values"]:
            self.combo_coluna.current(0)
        self._render_linhas()

    # -------------------------------------------------------------- #
    # Limpar todos os dados da tabela (TRUNCATE)                      #
    # -------------------------------------------------------------- #
    def limpar_tabela(self):
        if not self.tabela_atual:
            messagebox.showinfo("Atencao", "Selecione uma tabela primeiro.")
            return

        tabela = self.tabela_atual
        total = len(self.dados_completos)

        if not messagebox.askyesno(
                "Confirmar limpeza",
                f"Isso vai APAGAR TODOS os {total} registro(s) da tabela "
                f"'{tabela}'.\n\nEssa acao NAO pode ser desfeita.\n\n"
                f"Tem certeza que deseja continuar?",
                icon="warning"):
            return

        # Segunda confirmacao com digitacao para evitar acidentes
        confirma = simpledialog.askstring(
            "Confirmacao final",
            f"Para confirmar, digite o nome da tabela:\n\n{tabela}",
            parent=self.root)
        if confirma != tabela:
            messagebox.showinfo("Cancelado",
                                "Nome nao confere. Operacao cancelada.")
            return

        try:
            cur = self.conn.cursor()
            try:
                cur.execute(f"TRUNCATE TABLE `{tabela}`")
            except Exception:
                # Fallback (ex.: tabela com FK que impede TRUNCATE)
                cur.execute(f"DELETE FROM `{tabela}`")
            self.conn.commit()
            cur.close()
            messagebox.showinfo(
                "Concluido",
                f"Todos os dados da tabela '{tabela}' foram apagados.")
            self._exibir_dados_tabela(tabela)
        except Exception as e:
            try:
                self.conn.rollback()
            except Exception:
                pass
            messagebox.showerror(
                "Erro",
                f"Nao foi possivel limpar a tabela:\n{e}")

    # -------------------------------------------------------------- #
    # Clonar / copiar registro                                       #
    # -------------------------------------------------------------- #
    def clonar_registro(self):
        if not self.tabela_atual:
            messagebox.showinfo("Atencao", "Selecione uma tabela primeiro.")
            return
        sel = self.tree.selection()
        if not sel:
            messagebox.showinfo(
                "Atencao",
                "Selecione (clique) a linha que deseja clonar.")
            return
        if len(sel) > 1:
            messagebox.showinfo(
                "Atencao", "Selecione apenas UMA linha para clonar.")
            return

        item = sel[0]
        valores = list(self.tree.item(item, "values"))

        if not messagebox.askyesno(
                "Clonar registro",
                "Deseja criar uma copia (clone) desta linha na tabela "
                f"'{self.tabela_atual}'?"):
            return

        # Descobrir colunas auto_increment para nao copiar (deixa o banco gerar)
        colunas_incluir = []
        valores_incluir = []
        try:
            cur = self.conn.cursor()
            cur.execute(f"SHOW COLUMNS FROM `{self.tabela_atual}`")
            info_cols = cur.fetchall()  # (Field, Type, Null, Key, Default, Extra)
            auto_cols = set()
            for row in info_cols:
                field = row[0]
                extra = (row[5] or "").lower() if len(row) > 5 else ""
                if "auto_increment" in extra:
                    auto_cols.add(field)

            for idx, coluna in enumerate(self.colunas_atual):
                # Pula colunas auto_increment (inclusive a PK auto)
                if coluna in auto_cols:
                    continue
                val = valores[idx] if idx < len(valores) else ""
                colunas_incluir.append(coluna)
                valores_incluir.append(None if val == "" else val)

            if not colunas_incluir:
                messagebox.showwarning(
                    "Nao foi possivel clonar",
                    "Todas as colunas sao auto_increment; nada para copiar.")
                cur.close()
                return

            cols_sql = ", ".join(f"`{c}`" for c in colunas_incluir)
            placeholders = ", ".join(["%s"] * len(colunas_incluir))
            sql = (f"INSERT INTO `{self.tabela_atual}` "
                   f"({cols_sql}) VALUES ({placeholders})")
            cur.execute(sql, valores_incluir)
            self.conn.commit()
            novo_id = cur.lastrowid
            cur.close()

            msg = "Registro clonado com sucesso!"
            if novo_id:
                msg += f"\nNovo ID gerado: {novo_id}"
            messagebox.showinfo("Sucesso", msg)
            self._exibir_dados_tabela(self.tabela_atual)
        except Exception as e:
            try:
                self.conn.rollback()
            except Exception:
                pass
            messagebox.showerror(
                "Erro ao clonar",
                f"Nao foi possivel clonar o registro:\n{e}\n\n"
                "Dica: se houver colunas UNIQUE (alem da PK), o valor\n"
                "duplicado pode impedir a copia.")

    # -------------------------------------------------------------- #
    # Backup e Restauracao do banco                                  #
    # -------------------------------------------------------------- #
    def backup_banco(self):
        if not self.banco_atual:
            messagebox.showinfo("Atencao", "Nenhum banco aberto.")
            return
        # Reaproveita a rotina de backup (pede pasta, mysqldump/manual)
        self._fazer_backup(self.banco_atual)

    def restaurar_banco(self):
        if not self.banco_atual:
            messagebox.showinfo("Atencao", "Nenhum banco aberto.")
            return

        arquivo = filedialog.askopenfilename(
            title="Escolha o arquivo .sql para restaurar",
            filetypes=[("Arquivos SQL", "*.sql"), ("Todos", "*.*")])
        if not arquivo:
            return

        if not messagebox.askyesno(
                "Confirmar restauracao",
                f"Isso vai EXECUTAR o script:\n{arquivo}\n\n"
                f"no banco '{self.banco_atual}'.\n\n"
                "Dados existentes podem ser SOBRESCRITOS ou APAGADOS.\n"
                "Recomendamos fazer um Backup antes.\n\n"
                "Deseja continuar?",
                icon="warning"):
            return

        try:
            with open(arquivo, "r", encoding="utf-8") as f:
                conteudo = f.read()
        except Exception as e:
            messagebox.showerror("Erro",
                                 f"Nao foi possivel ler o arquivo:\n{e}")
            return

        # Executa os comandos do script (separados por ';')
        try:
            cur = self.conn.cursor()
            cur.execute(f"USE `{self.banco_atual}`")
            executados = 0
            erros = []
            for comando in self._separar_comandos_sql(conteudo):
                cmd = comando.strip()
                if not cmd or cmd.startswith("--"):
                    continue
                try:
                    cur.execute(cmd)
                    # Consome resultados se houver
                    try:
                        cur.fetchall()
                    except Exception:
                        pass
                    executados += 1
                except Exception as e:
                    erros.append(str(e))
            self.conn.commit()
            cur.close()

            if erros:
                messagebox.showwarning(
                    "Restauracao concluida com avisos",
                    f"{executados} comando(s) executado(s).\n"
                    f"{len(erros)} erro(s):\n\n" + "\n".join(erros[:8]))
            else:
                messagebox.showinfo(
                    "Sucesso",
                    f"Restauracao concluida!\n"
                    f"{executados} comando(s) executado(s).")
            # Atualiza listas e tabela atual
            self._carregar_lista_tabelas()
            if self.tabela_atual:
                self._exibir_dados_tabela(self.tabela_atual)
        except Exception as e:
            try:
                self.conn.rollback()
            except Exception:
                pass
            messagebox.showerror("Erro na restauracao",
                                 f"Falha ao restaurar:\n{e}")

    @staticmethod
    def _separar_comandos_sql(script):
        """Separa comandos por ';' respeitando aspas simples/duplas e comentarios."""
        comandos = []
        atual = []
        aspas = None  # ' ou "
        i = 0
        n = len(script)
        while i < n:
            ch = script[i]
            # Comentario de linha --
            if aspas is None and ch == "-" and i + 1 < n and script[i + 1] == "-":
                # Pula ate o fim da linha
                while i < n and script[i] != "\n":
                    i += 1
                continue
            if aspas is None and ch in ("'", '"'):
                aspas = ch
                atual.append(ch)
            elif aspas is not None and ch == aspas:
                # Verifica escape por barra
                if i > 0 and script[i - 1] == "\\":
                    atual.append(ch)
                else:
                    aspas = None
                    atual.append(ch)
            elif aspas is None and ch == ";":
                comandos.append("".join(atual))
                atual = []
            else:
                atual.append(ch)
            i += 1
        if "".join(atual).strip():
            comandos.append("".join(atual))
        return comandos

    # -------------------------------------------------------------- #
    # SQL Interativo                                                 #
    # -------------------------------------------------------------- #
    def abrir_sql_interativo(self):
        if not self.conn:
            messagebox.showinfo("Atencao", "Sem conexao com o MySQL.")
            return

        win = tk.Toplevel(self.root)
        win.title(f"SQL Interativo - {self.banco_atual or ''}")
        win.geometry("900x600")
        self._centralizar(win, 900, 600)
        win.transient(self.root)

        topo = tk.Frame(win, bg="#6f42c1", height=44)
        topo.pack(fill="x")
        topo.pack_propagate(False)
        tk.Label(topo, text="SQL Interativo", font=("Segoe UI", 13, "bold"),
                 bg="#6f42c1", fg="white").pack(side="left", padx=16)
        tk.Label(topo, text=f"Banco: {self.banco_atual or '(nenhum)'}",
                 font=("Segoe UI", 10), bg="#6f42c1", fg="white").pack(
                     side="right", padx=16)

        # Area de entrada do codigo
        tk.Label(win, text="Digite o comando MySQL abaixo:",
                 font=("Segoe UI", 10), anchor="w").pack(
                     fill="x", padx=12, pady=(10, 2))
        txt = tk.Text(win, height=8, font=("Consolas", 11), wrap="none")
        txt.pack(fill="x", padx=12)
        txt.focus_set()

        botoes = tk.Frame(win)
        botoes.pack(fill="x", padx=12, pady=8)

        lbl_info = tk.Label(win, text="", font=("Segoe UI", 9),
                            fg="#555", anchor="w")
        lbl_info.pack(fill="x", padx=12)

        # Area de resultado (Treeview)
        cont_res = tk.Frame(win)
        cont_res.pack(expand=True, fill="both", padx=12, pady=(4, 12))
        vsb = ttk.Scrollbar(cont_res, orient="vertical")
        hsb = ttk.Scrollbar(cont_res, orient="horizontal")
        tree_res = ttk.Treeview(cont_res, show="headings",
                                yscrollcommand=vsb.set, xscrollcommand=hsb.set)
        vsb.config(command=tree_res.yview)
        hsb.config(command=tree_res.xview)
        tree_res.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns")
        hsb.grid(row=1, column=0, sticky="ew")
        cont_res.rowconfigure(0, weight=1)
        cont_res.columnconfigure(0, weight=1)

        def executar():
            sql = txt.get("1.0", tk.END).strip()
            if not sql:
                lbl_info.config(text="Digite um comando antes de executar.",
                                fg="red")
                return
            # Remove ; final para execucao unica
            comandos = [c for c in self._separar_comandos_sql(sql)
                        if c.strip() and not c.strip().startswith("--")]
            if not comandos:
                return

            tree_res.delete(*tree_res.get_children())
            tree_res["columns"] = ()

            try:
                cur = self.conn.cursor()
                if self.banco_atual:
                    cur.execute(f"USE `{self.banco_atual}`")
                total_afetadas = 0
                ultimo_com_resultado = None
                colunas = None
                linhas = None

                for cmd in comandos:
                    cur.execute(cmd)
                    if cur.description:  # SELECT/SHOW/DESCRIBE etc.
                        colunas = [d[0] for d in cur.description]
                        linhas = cur.fetchall()
                        ultimo_com_resultado = cmd
                    else:
                        total_afetadas += cur.rowcount

                self.conn.commit()
                cur.close()

                if colunas is not None:
                    tree_res["columns"] = colunas
                    for c in colunas:
                        tree_res.heading(c, text=c)
                        tree_res.column(c, width=140, minwidth=60, anchor="w")
                    for reg in linhas:
                        vals = ["" if v is None else str(v) for v in reg]
                        tree_res.insert("", tk.END, values=vals)
                    lbl_info.config(
                        text=f"{len(linhas)} linha(s) retornada(s).",
                        fg="green")
                else:
                    lbl_info.config(
                        text=f"Comando(s) executado(s). "
                             f"Linhas afetadas: {total_afetadas}.",
                        fg="green")

                # Se alterou dados, atualiza a tela principal
                self._carregar_lista_tabelas()
                if self.tabela_atual:
                    self._exibir_dados_tabela(self.tabela_atual)
            except Exception as e:
                try:
                    self.conn.rollback()
                except Exception:
                    pass
                lbl_info.config(text=f"ERRO: {e}", fg="red")
                messagebox.showerror("Erro no SQL", str(e), parent=win)

        tk.Button(botoes, text="Executar (F5)", font=("Segoe UI", 11, "bold"),
                  bg="#28a745", fg="white", relief="flat", cursor="hand2",
                  padx=16, pady=4, command=executar).pack(side="left")
        tk.Button(botoes, text="Limpar", font=("Segoe UI", 11),
                  bg="#dddddd", relief="flat", cursor="hand2",
                  padx=12, pady=4,
                  command=lambda: txt.delete("1.0", tk.END)).pack(
                      side="left", padx=6)
        tk.Label(botoes,
                 text="Dica: aceita varios comandos separados por ';'",
                 font=("Segoe UI", 9), fg="#777").pack(side="left", padx=10)

        win.bind("<F5>", lambda e: executar())

    # -------------------------------------------------------------- #
    # Edicao de celula (entry sobreposto)                            #
    # -------------------------------------------------------------- #
    def _editar_celula(self, event):
        if not self.pk_atual:
            messagebox.showwarning(
                "Sem chave primaria",
                "Esta tabela nao possui chave primaria (PRIMARY KEY).\n"
                "A edicao segura nao esta disponivel para evitar alterar\n"
                "registros errados.")
            return

        item = self.tree.identify_row(event.y)
        col = self.tree.identify_column(event.x)
        if not item or not col:
            return

        col_index = int(col.replace("#", "")) - 1
        if col_index < 0 or col_index >= len(self.colunas_atual):
            return

        nome_coluna = self.colunas_atual[col_index]
        x, y, w, h = self.tree.bbox(item, col)
        valor_atual = self.tree.set(item, nome_coluna)

        editor = tk.Entry(self.tree, font=("Segoe UI", 11))
        editor.place(x=x, y=y, width=w, height=h)
        editor.insert(0, valor_atual)
        editor.select_range(0, tk.END)
        editor.focus_set()

        def confirmar(evt=None):
            novo = editor.get()
            editor.destroy()
            if novo != valor_atual:
                self.tree.set(item, nome_coluna, novo)
                self.alteracoes.setdefault(item, {})[nome_coluna] = novo
                self.lbl_status.config(
                    text=f"Alteracao pendente em '{nome_coluna}'. "
                         f"Total pendente: {sum(len(v) for v in self.alteracoes.values())} "
                         f"campo(s). Clique em 'Salvar alteracoes'.")

        def cancelar(evt=None):
            editor.destroy()

        editor.bind("<Return>", confirmar)
        editor.bind("<FocusOut>", confirmar)
        editor.bind("<Escape>", cancelar)

    # -------------------------------------------------------------- #
    # Salvar alteracoes no MySQL                                     #
    # -------------------------------------------------------------- #
    def salvar_alteracoes(self):
        if not self.alteracoes:
            messagebox.showinfo("Nada para salvar",
                                "Nao ha alteracoes pendentes.")
            return
        if not self.pk_atual:
            messagebox.showerror(
                "Sem chave primaria",
                "Nao e possivel salvar sem uma chave primaria na tabela.")
            return

        pk_index = self.colunas_atual.index(self.pk_atual)
        total = 0
        erros = []

        try:
            cur = self.conn.cursor()
            for item, campos in self.alteracoes.items():
                original = self.linhas_originais.get(item)
                if original is None:
                    continue
                pk_valor = original[pk_index]

                set_clauses = []
                valores = []
                for coluna, novo in campos.items():
                    set_clauses.append(f"`{coluna}` = %s")
                    valores.append(None if novo == "" else novo)
                valores.append(pk_valor)

                sql = (f"UPDATE `{self.tabela_atual}` "
                       f"SET {', '.join(set_clauses)} "
                       f"WHERE `{self.pk_atual}` = %s")
                try:
                    cur.execute(sql, valores)
                    total += cur.rowcount
                except Exception as e:
                    erros.append(f"Linha PK={pk_valor}: {e}")

            self.conn.commit()
            cur.close()

            if erros:
                messagebox.showwarning(
                    "Salvo com avisos",
                    f"Algumas alteracoes falharam:\n\n" + "\n".join(erros))
            else:
                messagebox.showinfo(
                    "Sucesso",
                    f"Alteracoes salvas com sucesso!\n"
                    f"Linhas afetadas: {total}")

            # Recarrega para refletir estado real
            self.alteracoes = {}
            self._exibir_dados_tabela(self.tabela_atual)

        except Exception as e:
            try:
                self.conn.rollback()
            except Exception:
                pass
            messagebox.showerror("Erro ao salvar",
                                 f"Nao foi possivel salvar as alteracoes:\n{e}")


# ------------------------------------------------------------------ #
# Ponto de entrada                                                   #
# ------------------------------------------------------------------ #
def main():
    root = tk.Tk()
    EditorBancoApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
