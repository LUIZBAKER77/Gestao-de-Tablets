@echo off
setlocal
title Gerando GestorEscolar_Configurador.exe

echo ============================================
echo  Gerador do Configurador (.exe)
echo ============================================
echo.
echo Este script instala as bibliotecas necessarias
echo e gera um unico arquivo .exe portavel.
echo.
echo Isso so precisa ser feito UMA VEZ (ou quando
echo o codigo do gestor_escolar_gui.py for alterado).
echo.
pause

where python >nul 2>&1
if errorlevel 1 (
    echo.
    echo ERRO: Python nao encontrado.
    echo Instale em https://www.python.org/downloads/
    echo IMPORTANTE: marque a opcao "Add Python to PATH" durante a instalacao.
    echo.
    pause
    exit /b 1
)

echo.
echo Instalando dependencias...
python -m pip install --upgrade pip -q
python -m pip install customtkinter pyinstaller -q

if errorlevel 1 (
    echo.
    echo ERRO ao instalar as dependencias. Verifique sua conexao com a internet.
    pause
    exit /b 1
)

echo.
echo Gerando o executavel (isso pode levar 1-2 minutos)...
python -m PyInstaller --onefile --windowed --noconfirm ^
    --name "GestorEscolar_Configurador" ^
    gestor_escolar_gui.py

if errorlevel 1 (
    echo.
    echo ERRO ao gerar o executavel. Veja as mensagens acima.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  PRONTO!
echo ============================================
echo.
echo O arquivo esta em:
echo   dist\GestorEscolar_Configurador.exe
echo.
echo Voce pode copiar esse .exe para qualquer PC da
echo escola e usar direto, sem precisar instalar nada.
echo.
pause
