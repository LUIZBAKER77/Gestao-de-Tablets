# Configurador para Windows

Programa com interface gráfica (CustomTkinter) que instala o APK e aplica o Device Owner nos tablets via ADB, para quem vai preparar os aparelhos não precisar abrir o terminal.

Na primeira execução ele baixa o Android Platform Tools sozinho. Dentro do programa há uma aba **Modo de Usar** com o passo a passo.

## Rodar pelo código

```bat
pip install customtkinter
python gestor_escolar_gui.py
```

## Gerar o executável

Execute `build_exe.bat`. Ele instala `customtkinter` e `pyinstaller` e gera `dist\GestorEscolar_Configurador.exe`, um arquivo único que roda em qualquer Windows sem Python.

## Fluxo de uso

1. Abra o configurador e espere aparecer **ADB pronto**.
2. No tablet: ative a depuração USB, remova contas e senha de bloqueio.
3. Conecte o tablet por USB e aceite o pedido de autorização.
4. Na aba **Configurar Tablet**, escolha o APK e clique em **Instalar e Configurar Tablet**.
