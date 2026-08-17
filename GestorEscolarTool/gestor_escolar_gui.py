# -*- coding: utf-8 -*-
"""
Gestor Escolar - Configurador de Tablets
Colegio Santa Catarina

App desktop com interface grafica para instalar e configurar o app
Gestor Escolar nos tablets da escola via ADB, sem precisar de terminal.

Requisitos para RODAR a partir do codigo fonte:
    pip install customtkinter

Para gerar o .exe, use o build_exe.bat incluso (precisa de Python instalado).
"""

import os
import sys
import time
import shutil
import zipfile
import hashlib
import threading
import subprocess
import urllib.request
import tkinter as tk
from tkinter import filedialog, messagebox
import customtkinter as ctk

# ---------------------------------------------------------------------------
# Configuracao geral
# ---------------------------------------------------------------------------

APP_ID = "com.escola.tabletmanager"
ADMIN_COMPONENT = f"{APP_ID}/.SchoolDeviceAdminReceiver"
ADB_DOWNLOAD_URL = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")

AZUL = "#1565C0"
AZUL_ESCURO = "#0D47A1"
VERDE = "#2E7D32"
VERMELHO = "#B71C1C"
CINZA = "#37474F"


def get_base_dir() -> str:
    """Retorna a pasta onde o .exe (ou o .py) esta rodando."""
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.abspath(__file__))


BASE_DIR = get_base_dir()
ADB_DIR = os.path.join(BASE_DIR, "platform-tools")
ADB_EXE = os.path.join(ADB_DIR, "adb.exe")
EXTRACTED_APPS_DIR = os.path.join(BASE_DIR, "apps_extraidos")

# Nao extrair/reinstalar o proprio Gestor Escolar por aqui — ele tem fluxo dedicado
PACOTES_IGNORADOS = {APP_ID}


# ---------------------------------------------------------------------------
# Funcoes utilitarias de ADB (rodam em threads separadas, chamadas pela UI)
# ---------------------------------------------------------------------------

def adb_available() -> bool:
    return os.path.isfile(ADB_EXE)


def download_and_extract_adb(progress_cb, log_cb):
    """Baixa o platform-tools oficial do Google e extrai ao lado do app."""
    tmp_zip = os.path.join(BASE_DIR, "_platform-tools-tmp.zip")

    log_cb("Baixando ADB (platform-tools) do site oficial do Google...")

    def reporthook(block_num, block_size, total_size):
        if total_size > 0:
            pct = min(100, int(block_num * block_size * 100 / total_size))
            progress_cb(pct)

    try:
        urllib.request.urlretrieve(ADB_DOWNLOAD_URL, tmp_zip, reporthook=reporthook)
    except Exception as e:
        log_cb(f"ERRO ao baixar ADB: {e}", "erro")
        return False

    log_cb("Download concluido. Extraindo...")
    try:
        with zipfile.ZipFile(tmp_zip, "r") as z:
            z.extractall(BASE_DIR)
    except Exception as e:
        log_cb(f"ERRO ao extrair ADB: {e}", "erro")
        return False
    finally:
        if os.path.exists(tmp_zip):
            os.remove(tmp_zip)

    if adb_available():
        log_cb("ADB pronto para uso.", "ok")
        return True
    else:
        log_cb("ADB baixado, mas adb.exe nao foi encontrado apos extrair.", "erro")
        return False


def run_adb(args, timeout=30):
    """Roda um comando adb e devolve (codigo, stdout+stderr)."""
    if not adb_available():
        return 1, "ADB nao encontrado."
    try:
        result = subprocess.run(
            [ADB_EXE] + args,
            capture_output=True,
            text=True,
            timeout=timeout,
            creationflags=subprocess.CREATE_NO_WINDOW if hasattr(subprocess, "CREATE_NO_WINDOW") else 0,
        )
        out = (result.stdout or "") + (result.stderr or "")
        return result.returncode, out.strip()
    except subprocess.TimeoutExpired:
        return 1, "Tempo esgotado esperando resposta do tablet."
    except Exception as e:
        return 1, f"Erro ao executar ADB: {e}"


def device_connected() -> bool:
    code, out = run_adb(["get-state"], timeout=6)
    return code == 0 and "device" in out.lower()


def list_devices_raw() -> str:
    _, out = run_adb(["devices"])
    return out


def sha256_of_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def list_third_party_packages():
    """Lista apps instalados pelo usuario (nao apps de sistema)."""
    _, out = run_adb(["shell", "pm", "list", "packages", "-3"])
    pkgs = []
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            pkg = line.split("package:", 1)[1].strip()
            if pkg and pkg not in PACOTES_IGNORADOS:
                pkgs.append(pkg)
    return pkgs


def get_apk_paths(pkg: str):
    """Retorna todos os caminhos de APK de um pacote no tablet (base + splits)."""
    _, out = run_adb(["shell", "pm", "path", pkg])
    paths = []
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            paths.append(line.split("package:", 1)[1].strip())
    return paths


# ---------------------------------------------------------------------------
# Interface grafica
# ---------------------------------------------------------------------------

class App(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("Gestor Escolar - Configurador de Tablets")
        self.geometry("980x680")
        self.minsize(860, 600)

        self.apk_path = tk.StringVar(value="")
        self.adb_status_var = tk.StringVar(value="Verificando ADB...")
        self._modo_massa_ativo = False

        self._build_header()
        self._build_tabs()

        self.after(300, self._startup_check_adb)

    # ---------------------------------------------------------- header ----
    def _build_header(self):
        header = ctk.CTkFrame(self, fg_color=AZUL_ESCURO, height=64, corner_radius=0)
        header.pack(fill="x", side="top")
        header.pack_propagate(False)

        title = ctk.CTkLabel(
            header, text="📱  Gestor Escolar — Configurador de Tablets",
            font=ctk.CTkFont(size=18, weight="bold"), text_color="white"
        )
        title.pack(side="left", padx=20)

        self.adb_badge = ctk.CTkLabel(
            header, textvariable=self.adb_status_var,
            font=ctk.CTkFont(size=12), text_color="#BBDEFB"
        )
        self.adb_badge.pack(side="right", padx=20)

    # -------------------------------------------------------------- tabs ----
    def _build_tabs(self):
        self.tabview = ctk.CTkTabview(self, segmented_button_selected_color=AZUL,
                                       segmented_button_selected_hover_color=AZUL_ESCURO)
        self.tabview.pack(fill="both", expand=True, padx=14, pady=14)

        self.tab_inicio = self.tabview.add("🏠 Início")
        self.tab_config = self.tabview.add("⚙️ Configurar Tablet")
        self.tab_massa = self.tabview.add("🚀 Modo em Massa")
        self.tab_contas = self.tabview.add("🧹 Contas / Usuários")
        self.tab_extrair = self.tabview.add("📦 Extrair APK")
        self.tab_failsafe = self.tabview.add("🆘 Fail-safe")
        self.tab_ajuda = self.tabview.add("❓ Modo de Usar")

        self._build_tab_inicio()
        self._build_tab_config()
        self._build_tab_massa()
        self._build_tab_contas()
        self._build_tab_extrair()
        self._build_tab_failsafe()
        self._build_tab_ajuda()

    # ------------------------------------------------------------ Início ----
    def _build_tab_inicio(self):
        f = self.tab_inicio

        card = ctk.CTkFrame(f, fg_color=CINZA, corner_radius=14)
        card.pack(fill="x", padx=10, pady=10)

        ctk.CTkLabel(card, text="Status do Tablet", font=ctk.CTkFont(size=15, weight="bold")).pack(
            anchor="w", padx=18, pady=(16, 6))

        self.lbl_device_status = ctk.CTkLabel(
            card, text="Nenhum tablet conectado.", font=ctk.CTkFont(size=13), justify="left"
        )
        self.lbl_device_status.pack(anchor="w", padx=18, pady=(0, 16))

        btn_row = ctk.CTkFrame(f, fg_color="transparent")
        btn_row.pack(fill="x", padx=10, pady=(0, 10))

        ctk.CTkButton(btn_row, text="🔄 Verificar Status", fg_color=AZUL,
                       command=self.action_verificar_status).pack(side="left", padx=(0, 10))
        ctk.CTkButton(btn_row, text="⚙️ Ir para Configurar Tablet", fg_color=VERDE,
                       command=lambda: self.tabview.set("⚙️ Configurar Tablet")).pack(side="left")

        info = ctk.CTkTextbox(f, fg_color="#111827", corner_radius=10)
        info.pack(fill="both", expand=True, padx=10, pady=10)
        info.insert("1.0",
            "Bem-vindo!\n\n"
            "Este programa configura os tablets do Colegio Santa Catarina com o app "
            "Gestor Escolar, sem precisar usar linha de comando.\n\n"
            "Antes de comecar, conecte o tablet no PC via cabo USB e:\n"
            "  1. Ative a Depuracao USB no tablet\n"
            "     (Configuracoes > Sobre o tablet > toque 7x em 'Numero da versao'\n"
            "      depois Configuracoes > Opcoes do desenvolvedor > Depuracao USB)\n"
            "  2. Aceite o popup de autorizacao USB que aparece no tablet\n"
            "  3. Remova qualquer conta Google/Samsung do tablet\n"
            "  4. Remova qualquer senha/PIN/padrao do tablet\n\n"
            "Depois, va na aba 'Configurar Tablet' para instalar e ativar tudo "
            "automaticamente."
        )
        info.configure(state="disabled")

    def action_verificar_status(self):
        threading.Thread(target=self._verificar_status_thread, daemon=True).start()

    def _verificar_status_thread(self):
        if not adb_available():
            self.after(0, lambda: self.lbl_device_status.configure(
                text="ADB ainda nao esta pronto. Aguarde o download terminar."))
            return

        if not device_connected():
            self.after(0, lambda: self.lbl_device_status.configure(
                text="❌ Nenhum tablet detectado. Verifique o cabo USB e a depuracao."))
            return

        _, model = run_adb(["shell", "getprop", "ro.product.model"])
        _, android_ver = run_adb(["shell", "getprop", "ro.build.version.release"])
        _, owners = run_adb(["shell", "dpm", "list-owners"])

        is_owner = APP_ID in owners
        owner_txt = "✅ Device Owner configurado" if is_owner else "❌ Device Owner NAO configurado"

        texto = (
            f"Modelo: {model.strip()}\n"
            f"Android: {android_ver.strip()}\n"
            f"{owner_txt}"
        )
        self.after(0, lambda: self.lbl_device_status.configure(text=texto))

    # -------------------------------------------------------- Configurar ----
    def _build_tab_config(self):
        f = self.tab_config

        row = ctk.CTkFrame(f, fg_color="transparent")
        row.pack(fill="x", padx=10, pady=(10, 4))

        ctk.CTkLabel(row, text="Arquivo APK:", font=ctk.CTkFont(size=13)).pack(side="left")
        self.entry_apk = ctk.CTkEntry(row, textvariable=self.apk_path, width=520,
                                       placeholder_text="Selecione o GestorEscolar.apk...")
        self.entry_apk.pack(side="left", padx=8, fill="x", expand=True)
        ctk.CTkButton(row, text="Procurar...", width=110, fg_color=CINZA,
                      command=self.action_escolher_apk).pack(side="left")

        btn_row = ctk.CTkFrame(f, fg_color="transparent")
        btn_row.pack(fill="x", padx=10, pady=8)

        ctk.CTkButton(btn_row, text="📲 Instalar e Configurar Tablet", height=42,
                      font=ctk.CTkFont(size=14, weight="bold"), fg_color=VERDE,
                      command=self.action_instalar_configurar).pack(side="left", padx=(0, 10))
        ctk.CTkButton(btn_row, text="🗑 Limpar Log", fg_color=CINZA,
                      command=lambda: self._clear_log(self.log_config)).pack(side="left")

        self.log_config = ctk.CTkTextbox(f, fg_color="#0b1220", corner_radius=10,
                                          font=ctk.CTkFont(family="Consolas", size=12))
        self.log_config.pack(fill="both", expand=True, padx=10, pady=(0, 10))
        self._setup_log_tags(self.log_config)
        self._log(self.log_config, "Pronto. Conecte o tablet, escolha o APK e clique em Instalar.")

    def action_escolher_apk(self):
        path = filedialog.askopenfilename(
            title="Selecione o APK do Gestor Escolar",
            filetypes=[("Arquivo APK", "*.apk")]
        )
        if path:
            self.apk_path.set(path)

    def action_instalar_configurar(self):
        if not self.apk_path.get():
            messagebox.showwarning("Atencao", "Selecione o arquivo APK primeiro.")
            return
        threading.Thread(target=self._instalar_configurar_thread, daemon=True).start()

    def _instalar_configurar_thread(self):
        log = self.log_config
        self._log(log, "Verificando conexao com o tablet...")

        if not adb_available():
            self._log(log, "ADB ainda nao esta pronto. Aguarde o download na barra de status.", "erro")
            return

        if not device_connected():
            self._log(log, "Nenhum tablet detectado. Verifique o cabo USB, a depuracao "
                            "e aceite o popup de autorizacao no tablet.", "erro")
            return

        self._log(log, "Tablet detectado. Instalando APK...")
        code, out = run_adb(["install", "-r", self.apk_path.get()], timeout=90)
        if code != 0 and "success" not in out.lower():
            self._log(log, f"Falha ao instalar o APK:\n{out}", "erro")
            return
        self._log(log, "APK instalado com sucesso.", "ok")

        self._log(log, "Configurando Device Owner...")
        code, out = run_adb(["shell", "dpm", "set-device-owner", ADMIN_COMPONENT], timeout=30)

        if "success" in out.lower():
            self._log(log, "SUCESSO! Device Owner configurado.", "ok")
            self._log(log, "Proximo passo no tablet: abrir o app, criar senha admin, "
                            "escolher apps permitidos e ativar o Modo Aluno.", "ok")
            self.after(0, self.action_verificar_status)
            return

        self._log(log, f"Falhou ao configurar Device Owner:\n{out}", "erro")

        low = out.lower()
        if "already several users" in low or "accounts on the device" in low:
            self._log(log, "Causa provavel: ha contas ou usuarios extras no tablet.\n"
                            "Va na aba 'Contas / Usuarios' para resolver isso.", "aviso")
        elif "already set" in low:
            self._log(log, "Ja existe um Device Owner configurado. Use a aba "
                            "'Fail-safe' para remover e tente de novo.", "aviso")
        elif "not installed" in low:
            self._log(log, "O app nao parece estar instalado. Tente instalar novamente.", "aviso")

    # -------------------------------------------------------- Modo Massa ----
    def _build_tab_massa(self):
        f = self.tab_massa

        ctk.CTkLabel(f, text="Modo em Massa — Configurar Vários Tablets Seguidos",
                     font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=10, pady=(10, 4))

        desc = ctk.CTkLabel(
            f, justify="left", font=ctk.CTkFont(size=12), text_color="#94a3b8",
            text=("Deixe isto rodando e va conectando os tablets um de cada vez.\n"
                  "Para cada tablet conectado, o programa automaticamente:\n"
                  "  1. Instala o Gestor Escolar\n"
                  "  2. Configura o Device Owner\n"
                  "  3. Instala os apps extraidos anteriormente (se houver)\n\n"
                  "Quando terminar, desconecte o tablet e conecte o proximo — o\n"
                  "programa detecta sozinho e comeca tudo de novo automaticamente.")
        )
        desc.pack(anchor="w", padx=10, pady=(0, 10))

        row = ctk.CTkFrame(f, fg_color="transparent")
        row.pack(fill="x", padx=10, pady=(0, 4))
        ctk.CTkLabel(row, text="APK do Gestor Escolar:", font=ctk.CTkFont(size=13)).pack(side="left")
        self.entry_apk_massa = ctk.CTkEntry(row, textvariable=self.apk_path, width=440,
                                             placeholder_text="Selecione o GestorEscolar.apk...")
        self.entry_apk_massa.pack(side="left", padx=8, fill="x", expand=True)
        ctk.CTkButton(row, text="Procurar...", width=110, fg_color=CINZA,
                      command=self.action_escolher_apk).pack(side="left")

        btn_row = ctk.CTkFrame(f, fg_color="transparent")
        btn_row.pack(fill="x", padx=10, pady=8)

        self.btn_iniciar_massa = ctk.CTkButton(
            btn_row, text="🚀 Iniciar Modo em Massa", height=42,
            font=ctk.CTkFont(size=14, weight="bold"), fg_color=VERDE,
            command=self.action_iniciar_massa)
        self.btn_iniciar_massa.pack(side="left", padx=(0, 10))

        self.btn_parar_massa = ctk.CTkButton(
            btn_row, text="⏹ Parar", fg_color=VERMELHO,
            command=self.action_parar_massa, state="disabled")
        self.btn_parar_massa.pack(side="left")

        self.lbl_status_massa = ctk.CTkLabel(
            f, text="Parado.", font=ctk.CTkFont(size=14, weight="bold"))
        self.lbl_status_massa.pack(anchor="w", padx=10, pady=(4, 4))

        self.log_massa = ctk.CTkTextbox(f, fg_color="#0b1220", corner_radius=10,
                                         font=ctk.CTkFont(family="Consolas", size=12))
        self.log_massa.pack(fill="both", expand=True, padx=10, pady=(0, 10))
        self._setup_log_tags(self.log_massa)
        self._log(self.log_massa, "Selecione o APK, clique em Iniciar, e va conectando os tablets.")

    def action_iniciar_massa(self):
        if not self.apk_path.get():
            messagebox.showwarning("Atenção", "Selecione o arquivo APK do Gestor Escolar primeiro.")
            return
        if not adb_available():
            messagebox.showwarning("Atenção", "Aguarde o ADB terminar de baixar (veja a aba Início).")
            return

        self._modo_massa_ativo = True
        self.btn_iniciar_massa.configure(state="disabled")
        self.btn_parar_massa.configure(state="normal")
        self._clear_log(self.log_massa)
        self._log(self.log_massa, "Modo em massa iniciado. Aguardando tablets...")
        threading.Thread(target=self._loop_modo_massa, daemon=True).start()

    def action_parar_massa(self):
        self._modo_massa_ativo = False
        self.btn_iniciar_massa.configure(state="normal")
        self.btn_parar_massa.configure(state="disabled")
        self.lbl_status_massa.configure(text="Parado.")
        self._log(self.log_massa, "Modo em massa parado.")

    def _loop_modo_massa(self):
        log = self.log_massa

        while self._modo_massa_ativo:
            self.after(0, lambda: self.lbl_status_massa.configure(
                text="🔌 Aguardando tablet... conecte via USB"))

            while self._modo_massa_ativo and not device_connected():
                time.sleep(1.5)

            if not self._modo_massa_ativo:
                break

            self.after(0, lambda: self.lbl_status_massa.configure(text="⚙️ Configurando tablet..."))
            self._log(log, "═══ Tablet detectado! Iniciando configuração automática... ═══")

            self._configurar_tablet_completo(log)

            self._log(log, "Concluído neste tablet. Desconecte e conecte o próximo "
                            "(ou clique em Parar para finalizar).", "ok")
            self.after(0, lambda: self.lbl_status_massa.configure(
                text="✅ Pronto! Desconecte e conecte o próximo tablet."))

            # Espera desconectar antes de tentar de novo, para nao repetir no mesmo tablet
            while self._modo_massa_ativo and device_connected():
                time.sleep(1.5)

        self.after(0, lambda: self.lbl_status_massa.configure(text="Parado."))

    def _configurar_tablet_completo(self, log):
        """Roda o fluxo completo: instala APK, configura Device Owner, instala apps extraidos."""

        self._log(log, "1/3 — Instalando o Gestor Escolar...")
        code, out = run_adb(["install", "-r", self.apk_path.get()], timeout=90)
        if "success" not in out.lower():
            self._log(log, f"Falha ao instalar o APK:\n{out}", "erro")
            return
        self._log(log, "APK instalado.", "ok")

        self._log(log, "2/3 — Configurando Device Owner...")
        code, out = run_adb(["shell", "dpm", "set-device-owner", ADMIN_COMPONENT], timeout=30)
        if "success" in out.lower():
            self._log(log, "Device Owner configurado.", "ok")
        else:
            self._log(log, f"Falha ao configurar Device Owner:\n{out}", "erro")
            low = out.lower()
            if "already several users" in low or "accounts on the device" in low:
                self._log(log, "Há contas/usuários extras neste tablet. Resolva na aba "
                                "'Contas / Usuários' e configure este tablet manualmente.", "aviso")
            elif "already set" in low:
                self._log(log, "Já existe Device Owner. Use a aba Fail-safe para remover.", "aviso")
            # Mesmo sem Device Owner, tenta seguir instalando os apps extraidos

        if os.path.isdir(EXTRACTED_APPS_DIR) and os.listdir(EXTRACTED_APPS_DIR):
            self._log(log, "3/3 — Instalando apps extraídos anteriormente...")
            pkg_dirs = [d for d in os.listdir(EXTRACTED_APPS_DIR)
                        if os.path.isdir(os.path.join(EXTRACTED_APPS_DIR, d))]
            sucesso, falha = 0, 0
            for pkg in pkg_dirs:
                pkg_dir = os.path.join(EXTRACTED_APPS_DIR, pkg)
                apk_files = [os.path.join(pkg_dir, fn) for fn in os.listdir(pkg_dir)
                             if fn.lower().endswith(".apk")]
                if not apk_files:
                    continue
                if len(apk_files) == 1:
                    _, out2 = run_adb(["install", "-r", apk_files[0]], timeout=90)
                else:
                    _, out2 = run_adb(["install-multiple", "-r"] + apk_files, timeout=120)
                if "success" in out2.lower():
                    sucesso += 1
                else:
                    falha += 1
            self._log(log, f"Apps extraídos: {sucesso} instalado(s), {falha} falha(s).",
                       "ok" if falha == 0 else "aviso")
        else:
            self._log(log, "3/3 — Nenhum app extraído para instalar (etapa pulada).")


    def _build_tab_contas(self):
        f = self.tab_contas

        ctk.CTkLabel(f, text="Contas e Usuários do Tablet",
                     font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=10, pady=(10, 4))

        desc = ctk.CTkLabel(
            f, justify="left", font=ctk.CTkFont(size=12), text_color="#94a3b8",
            text=("Em alguns tablets Samsung, o Android recusa configurar o Device Owner\n"
                  "porque existem contas ou usuarios extras (mesmo invisiveis). Use os\n"
                  "botoes abaixo para verificar e tentar corrigir isso.")
        )
        desc.pack(anchor="w", padx=10, pady=(0, 10))

        btn_row = ctk.CTkFrame(f, fg_color="transparent")
        btn_row.pack(fill="x", padx=10, pady=6)

        ctk.CTkButton(btn_row, text="👤 Ver Usuários/Perfis", fg_color=AZUL,
                      command=self.action_ver_usuarios).pack(side="left", padx=(0, 10))
        ctk.CTkButton(btn_row, text="🧹 Limpar Contas Invisíveis", fg_color=VERMELHO,
                      command=self.action_limpar_contas).pack(side="left")

        self.log_contas = ctk.CTkTextbox(f, fg_color="#0b1220", corner_radius=10,
                                          font=ctk.CTkFont(family="Consolas", size=12))
        self.log_contas.pack(fill="both", expand=True, padx=10, pady=10)
        self._setup_log_tags(self.log_contas)
        self._log(self.log_contas, "Conecte o tablet e clique em um dos botoes acima.")

    def action_ver_usuarios(self):
        threading.Thread(target=self._ver_usuarios_thread, daemon=True).start()

    def _ver_usuarios_thread(self):
        log = self.log_contas
        if not device_connected():
            self._log(log, "Nenhum tablet detectado.", "erro")
            return
        _, out1 = run_adb(["shell", "pm", "list", "users"])
        _, out2 = run_adb(["shell", "cmd", "user", "list"])
        self._log(log, "Usuarios (pm list users):\n" + out1)
        self._log(log, "Usuarios (cmd user list):\n" + out2)
        self._log(log, "Se aparecer algo alem do usuario 0, remova no proprio tablet: "
                        "Convidado, Outro usuario, Perfil de trabalho, Pasta Segura, Modo manutencao.", "aviso")

    def action_limpar_contas(self):
        threading.Thread(target=self._limpar_contas_thread, daemon=True).start()

    def _limpar_contas_thread(self):
        log = self.log_contas
        if not device_connected():
            self._log(log, "Nenhum tablet detectado.", "erro")
            return

        self._log(log, "Limpando dados de contas Samsung/Google conhecidas...")
        pacotes = [
            "com.osp.app.signin",
            "com.samsung.android.mobileservice",
            "com.google.android.gsf",
            "com.google.android.gms",
        ]
        for pkg in pacotes:
            run_adb(["shell", "pm", "clear", pkg])
            self._log(log, f"  - {pkg} limpo")

        run_adb(["shell", "pm", "disable-user", "--user", "0", "com.samsung.knox.securefolder"])
        self._log(log, "Pasta Segura desativada (se existia).")

        self._log(log, "Testando Device Owner novamente...")
        code, out = run_adb(["shell", "dpm", "set-device-owner", ADMIN_COMPONENT])
        if "success" in out.lower():
            self._log(log, "SUCESSO! Device Owner configurado apos limpeza.", "ok")
        else:
            self._log(log, "Ainda nao foi possivel. Pode ser necessario remover manualmente:\n"
                            "Configuracoes > Contas e backup > Gerenciar contas > remover tudo\n"
                            "Configuracoes > Biometria e seguranca > Pasta Segura > excluir\n"
                            "Configuracoes > Gerenciamento geral > Usuarios > remover Convidado\n"
                            "Depois reinicie o tablet e tente de novo.", "aviso")

    # -------------------------------------------------------- Extrair ----
    def _build_tab_extrair(self):
        f = self.tab_extrair

        ctk.CTkLabel(f, text="Apps do Tablet — Backup e Instalação em Massa",
                     font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=10, pady=(10, 4))

        desc = ctk.CTkLabel(
            f, justify="left", font=ctk.CTkFont(size=12), text_color="#94a3b8",
            text=("1) Extraia todos os apps de um tablet ja configurado (o \"modelo\").\n"
                  "2) Depois, em qualquer outro tablet novo, clique em \"Instalar Apps\"\n"
                  "   para instalar todos de uma vez, sem precisar baixar da Play Store.\n\n"
                  f"Os apps ficam salvos em:\n{EXTRACTED_APPS_DIR}")
        )
        desc.pack(anchor="w", padx=10, pady=(0, 10))

        btn_row = ctk.CTkFrame(f, fg_color="transparent")
        btn_row.pack(fill="x", padx=10, pady=6)

        ctk.CTkButton(btn_row, text="📤 Extrair Todos os Apps do Tablet", fg_color=AZUL,
                      command=self.action_extrair_todos).pack(side="left", padx=(0, 10))
        ctk.CTkButton(btn_row, text="📥 Instalar Apps Extraídos", fg_color=VERDE,
                      command=self.action_instalar_todos).pack(side="left")

        self.log_extrair = ctk.CTkTextbox(f, fg_color="#0b1220", corner_radius=10,
                                           font=ctk.CTkFont(family="Consolas", size=12))
        self.log_extrair.pack(fill="both", expand=True, padx=10, pady=10)
        self._setup_log_tags(self.log_extrair)
        self._log(self.log_extrair, "Conecte o tablet e escolha uma das opcoes acima.")

    def action_extrair_todos(self):
        confirmado = messagebox.askyesno(
            "Extrair todos os apps",
            "Isso vai copiar todos os apps instalados (nao-sistema) do tablet\n"
            "conectado para o PC. Pode levar alguns minutos dependendo da\n"
            "quantidade de apps.\n\nContinuar?"
        )
        if confirmado:
            threading.Thread(target=self._extrair_todos_thread, daemon=True).start()

    def _extrair_todos_thread(self):
        log = self.log_extrair
        if not device_connected():
            self._log(log, "Nenhum tablet detectado.", "erro")
            return

        self._log(log, "Listando apps instalados no tablet...")
        pkgs = list_third_party_packages()
        if not pkgs:
            self._log(log, "Nenhum app de terceiros encontrado neste tablet.", "aviso")
            return

        self._log(log, f"{len(pkgs)} app(s) encontrado(s). Iniciando extracao...")
        os.makedirs(EXTRACTED_APPS_DIR, exist_ok=True)

        sucesso, falha = 0, 0
        for i, pkg in enumerate(pkgs, 1):
            self._log(log, f"[{i}/{len(pkgs)}] Extraindo {pkg}...")
            remote_paths = get_apk_paths(pkg)
            if not remote_paths:
                self._log(log, f"  Nao foi possivel localizar o APK de {pkg}", "erro")
                falha += 1
                continue

            pkg_dir = os.path.join(EXTRACTED_APPS_DIR, pkg)
            os.makedirs(pkg_dir, exist_ok=True)

            ok_pkg = True
            for remote in remote_paths:
                filename = os.path.basename(remote)
                local_path = os.path.join(pkg_dir, filename)
                run_adb(["pull", remote, local_path], timeout=60)
                if not os.path.exists(local_path):
                    ok_pkg = False

            if ok_pkg:
                sucesso += 1
            else:
                falha += 1
                self._log(log, f"  Falha ao copiar um ou mais arquivos de {pkg}", "erro")

        self._log(log, f"Concluido! {sucesso} app(s) extraido(s), {falha} falha(s).",
                   "ok" if falha == 0 else "aviso")
        self._log(log, f"Apps salvos em: {EXTRACTED_APPS_DIR}")

    def action_instalar_todos(self):
        if not os.path.isdir(EXTRACTED_APPS_DIR) or not os.listdir(EXTRACTED_APPS_DIR):
            messagebox.showinfo("Nenhum app extraido",
                                 "Ainda nao ha apps extraidos. Use \"Extrair Todos os Apps\" "
                                 "em um tablet ja configurado primeiro.")
            return
        confirmado = messagebox.askyesno(
            "Instalar apps no tablet",
            "Isso vai instalar todos os apps extraidos anteriormente no\n"
            "tablet conectado agora. Pode levar alguns minutos.\n\nContinuar?"
        )
        if confirmado:
            threading.Thread(target=self._instalar_todos_thread, daemon=True).start()

    def _instalar_todos_thread(self):
        log = self.log_extrair
        if not device_connected():
            self._log(log, "Nenhum tablet detectado.", "erro")
            return

        pkg_dirs = [d for d in os.listdir(EXTRACTED_APPS_DIR)
                    if os.path.isdir(os.path.join(EXTRACTED_APPS_DIR, d))]
        if not pkg_dirs:
            self._log(log, "Pasta de apps extraidos esta vazia.", "erro")
            return

        self._log(log, f"{len(pkg_dirs)} app(s) para instalar neste tablet...")

        sucesso, falha = 0, 0
        for i, pkg in enumerate(pkg_dirs, 1):
            pkg_dir = os.path.join(EXTRACTED_APPS_DIR, pkg)
            apk_files = [os.path.join(pkg_dir, fn) for fn in os.listdir(pkg_dir)
                         if fn.lower().endswith(".apk")]
            if not apk_files:
                continue

            self._log(log, f"[{i}/{len(pkg_dirs)}] Instalando {pkg}...")

            if len(apk_files) == 1:
                code, out = run_adb(["install", "-r", apk_files[0]], timeout=90)
            else:
                # App com split APKs (base + configuracoes de idioma/tela/etc)
                code, out = run_adb(["install-multiple", "-r"] + apk_files, timeout=120)

            if "success" in out.lower():
                sucesso += 1
            else:
                falha += 1
                self._log(log, f"  Falha ao instalar {pkg}:\n  {out}", "erro")

        self._log(log, f"Concluido! {sucesso} app(s) instalado(s), {falha} falha(s).",
                   "ok" if falha == 0 else "aviso")

    # -------------------------------------------------------- Fail-safe ----
    def _build_tab_failsafe(self):
        f = self.tab_failsafe

        ctk.CTkLabel(f, text="Fail-safe / Restauração",
                     font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=10, pady=(10, 4))

        desc = ctk.CTkLabel(
            f, justify="left", font=ctk.CTkFont(size=12), text_color="#94a3b8",
            text="Use isto se precisar remover o Device Owner de um tablet\n"
                 "(por exemplo, para reconfigurar do zero)."
        )
        desc.pack(anchor="w", padx=10, pady=(0, 10))

        ctk.CTkButton(f, text="⚠️ Remover Device Owner", fg_color=VERMELHO,
                      command=self.action_remover_owner).pack(anchor="w", padx=10, pady=6)

        self.log_failsafe = ctk.CTkTextbox(f, fg_color="#0b1220", corner_radius=10,
                                            font=ctk.CTkFont(family="Consolas", size=12))
        self.log_failsafe.pack(fill="both", expand=True, padx=10, pady=10)
        self._setup_log_tags(self.log_failsafe)
        self._log(self.log_failsafe, "Conecte o tablet e clique no botao acima, se necessario.")

    def action_remover_owner(self):
        confirmado = messagebox.askyesno(
            "Confirmar remoção",
            "Isso remove o Device Owner do tablet conectado.\n\nTem certeza?"
        )
        if confirmado:
            threading.Thread(target=self._remover_owner_thread, daemon=True).start()

    def _remover_owner_thread(self):
        log = self.log_failsafe
        if not device_connected():
            self._log(log, "Nenhum tablet detectado.", "erro")
            return

        code, out = run_adb(["shell", "dpm", "remove-active-admin", "--user", "0", ADMIN_COMPONENT])
        self._log(log, out or "Comando executado.")
        self._log(log, "Se nao funcionou, abra o app no tablet, entre no modo Admin "
                        "e use o botao Remover dentro do proprio app.", "aviso")

    # -------------------------------------------------------------- Ajuda ----
    def _build_tab_ajuda(self):
        f = self.tab_ajuda
        box = ctk.CTkTextbox(f, fg_color="#0b1220", corner_radius=10, font=ctk.CTkFont(size=13))
        box.pack(fill="both", expand=True, padx=10, pady=10)
        box.insert("1.0", MODO_DE_USAR_TEXTO)
        box.configure(state="disabled")

    # -------------------------------------------------------------- ADB ----
    def _startup_check_adb(self):
        if adb_available():
            self.adb_status_var.set("✅ ADB pronto")
            return
        self.adb_status_var.set("⬇ Baixando ADB...")
        threading.Thread(target=self._baixar_adb_thread, daemon=True).start()

    def _baixar_adb_thread(self):
        def progress_cb(pct):
            self.after(0, lambda: self.adb_status_var.set(f"⬇ Baixando ADB... {pct}%"))

        def log_cb(msg, tipo="info"):
            self.after(0, lambda: self.adb_status_var.set(msg[:60]))

        ok = download_and_extract_adb(progress_cb, log_cb)
        if ok:
            self.after(0, lambda: self.adb_status_var.set("✅ ADB pronto"))
        else:
            self.after(0, lambda: self.adb_status_var.set("❌ Falha ao baixar ADB (ver Início)"))

    # -------------------------------------------------------- Log helpers ----
    def _setup_log_tags(self, textbox: ctk.CTkTextbox):
        try:
            inner = textbox._textbox
            inner.tag_config("ok", foreground="#4ade80")
            inner.tag_config("erro", foreground="#f87171")
            inner.tag_config("aviso", foreground="#facc15")
            inner.tag_config("info", foreground="#e2e8f0")
        except Exception:
            pass

    def _log(self, textbox: ctk.CTkTextbox, msg: str, tag: str = "info"):
        def _do():
            textbox.configure(state="normal")
            try:
                inner = textbox._textbox
                inner.insert("end", msg + "\n\n", tag)
            except Exception:
                textbox.insert("end", msg + "\n\n")
            textbox.see("end")
            textbox.configure(state="normal")
        self.after(0, _do)

    def _clear_log(self, textbox: ctk.CTkTextbox):
        textbox.configure(state="normal")
        textbox.delete("1.0", "end")


MODO_DE_USAR_TEXTO = """\
COMO USAR — Gestor Escolar Configurador

1. PREPARAR O TABLET
   - Ligue o tablet e conclua o setup inicial (idioma, etc).
   - Va em Configuracoes > Sobre o tablet > toque 7x em "Numero da versao"
     ate aparecer "Voce agora e um desenvolvedor!".
   - Volte em Configuracoes > Opcoes do desenvolvedor > ative "Depuracao USB".
   - Remova qualquer conta Google/Samsung ja logada no tablet.
   - Remova qualquer senha, PIN ou padrao de bloqueio de tela.

2. CONECTAR
   - Conecte o tablet ao PC com um cabo USB de dados (nao so carregamento).
   - Um popup vai aparecer no tablet perguntando se autoriza a depuracao USB
     deste computador — toque em "Permitir" (marque "sempre permitir" se quiser).

3. CONFIGURAR (um tablet por vez)
   - Na aba "Configurar Tablet", clique em "Procurar..." e selecione o arquivo
     GestorEscolar.apk.
   - Clique em "Instalar e Configurar Tablet".
   - Aguarde a mensagem de sucesso.

   OU, PARA VARIOS TABLETS SEGUIDOS:
   - Va na aba "Modo em Massa", selecione o APK uma vez e clique em
     "Iniciar Modo em Massa".
   - Conecte o primeiro tablet. O programa instala, configura o Device
     Owner e instala os apps extraidos automaticamente.
   - Quando terminar, desconecte esse tablet e conecte o proximo — o
     programa detecta sozinho e repete tudo, sem precisar clicar em nada.
   - Clique em "Parar" quando terminar todos os tablets.

4. NO TABLET
   - Abra o app Gestor Escolar.
   - Crie a senha de administrador (anote em local seguro).
   - Escolha os apps que os alunos podem usar.
   - Ative o Modo Aluno.

BACKUP E INSTALAÇÃO DE APPS EM MASSA

   Depois de configurar um tablet "modelo" com todos os apps que os
   alunos vao usar, va na aba "Extrair APK" e clique em "Extrair Todos
   os Apps do Tablet". Isso salva uma copia de todos os apps no PC.

   Nos proximos tablets, clique em "Instalar Apps Extraídos" para
   instalar tudo de uma vez, sem precisar baixar cada app de novo na
   Play Store. Isso tambem roda automaticamente dentro do "Modo em
   Massa".

PROBLEMAS COMUNS

"Nao foi possivel configurar" / "ha contas no tablet":
   - Va na aba "Contas / Usuarios" e clique em "Limpar Contas Invisiveis".
   - Se nao resolver, siga as instrucoes manuais que aparecem no log.

"Ja existe um Device Owner":
   - Va na aba "Fail-safe" e clique em "Remover Device Owner", depois tente
     configurar de novo.

Tablet nao aparece / nao detectado:
   - Verifique se o cabo USB e de dados (nao so carregamento).
   - Verifique se a Depuracao USB esta ativada.
   - Verifique se voce aceitou o popup de autorizacao no tablet.

O app baixa o ADB sozinho na primeira vez que voce abre este programa —
isso aparece no cantinho superior direito da tela. So use os botoes das
outras abas depois que aparecer "ADB pronto".
"""


if __name__ == "__main__":
    app = App()
    app.mainloop()
