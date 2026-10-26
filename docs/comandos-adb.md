# Comandos ADB

Referência rápida para preparar e diagnosticar tablets. Os comandos com `findstr` são para o Prompt do Windows; no Linux/macOS troque por `grep -i`.

Antes de aplicar o Device Owner, confira:

- nenhuma conta Google no aparelho;
- sem senha, PIN ou padrão de bloqueio;
- APK já instalado;
- depuração USB autorizada para este computador.

## Instalação e Device Owner

```bat
adb devices
adb install -r GestorEscolar.apk
adb shell dpm set-device-owner com.escola.tabletmanager/.SchoolDeviceAdminReceiver
adb shell dpm list-owners
adb shell pm path com.escola.tabletmanager
```

## Informações do aparelho

```bat
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys device_policy | findstr /i "no_config_credentials password keyguard"
```

## Bloquear as telas de criação de senha

Impede que o aluno defina um PIN/padrão pelas Configurações. Os nomes dos componentes variam conforme a versão do Android e da One UI, então primeiro descubra qual atividade responde pela ação:

```bat
adb shell cmd package resolve-activity --brief -a android.app.action.SET_NEW_PASSWORD
```

Depois desative as telas:

```bat
adb shell pm disable-user --user 0 com.android.settings/.password.SetNewPasswordActivity
adb shell pm disable-user --user 0 com.android.settings/.password.ChooseLockGeneric
adb shell pm disable-user --user 0 com.android.settings/.password.ChooseLockPassword
adb shell pm disable-user --user 0 com.android.settings/.password.ChooseLockPattern
```

Para desfazer:

```bat
adb shell pm enable com.android.settings/.password.SetNewPasswordActivity
adb shell pm enable com.android.settings/.password.ChooseLockGeneric
adb shell pm enable com.android.settings/.password.ChooseLockPassword
adb shell pm enable com.android.settings/.password.ChooseLockPattern
```

## Logs e remoção

```bat
adb logcat -d | findstr /i "GestorEscolar Keyguard senha"
adb shell dpm remove-active-admin --user 0 com.escola.tabletmanager/.SchoolDeviceAdminReceiver
```

O último comando remove o Device Owner; use só em manutenção.
