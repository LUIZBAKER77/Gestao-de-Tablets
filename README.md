# Gestão de Tablets

App Android (Kotlin) para transformar tablets comuns em aparelhos de uso escolar: o aluno só vê os apps liberados pela escola, não consegue mexer nas configurações nem colocar senha no aparelho, e o administrador controla tudo por um painel protegido por senha.

O app roda como **Device Owner**, então usa as APIs de `DevicePolicyManager` diretamente, sem MDM pago.

## Funcionalidades

- **Modo aluno:** esconde e suspende os apps não autorizados (`setApplicationHidden`, `setPackagesSuspended`) e aplica restrições de usuário — sem instalar/desinstalar apps, sem reset de fábrica, sem modo de segurança, sem transferência USB, sem trocar papel de parede.
- **Painel do administrador:** lista os apps instalados, permite escolher quais ficam liberados e alterna entre modo aluno e modo administrador. O acesso é por senha, guardada como hash SHA-256.
- **Bloqueio de senha no aparelho:** se alguém define um PIN ou padrão, o app remove usando `resetPasswordWithToken`. Em aparelhos onde o escrow token não está disponível, um serviço de acessibilidade bloqueia as telas de criação de senha.
- **Atualização automática:** um `WorkManager` verifica uma nova versão a cada 6 horas e instala o APK sem intervenção.
- **Inicialização:** um `BootReceiver` reaplica as políticas quando o tablet liga.

## Estrutura

```
app/src/main/java/com/escola/tabletmanager/
├── MainActivity.kt                       tela inicial e troca de modo
├── PasswordActivity.kt                   entrada do administrador
├── AdminActivity.kt                      escolha dos apps liberados
├── EducationalAppsActivity.kt            atalhos para apps educacionais
├── AppManager.kt                         políticas do Device Owner e estado do app
├── PasswordTokenManager.kt               reset password token (remoção de PIN)
├── PasswordGuardAccessibilityService.kt  fallback quando não há token
├── SchoolDeviceAdminReceiver.kt          DeviceAdminReceiver
├── BootReceiver.kt
└── AutoUpdater.kt
GestorEscolarTool/                        configurador para Windows (Python)
docs/comandos-adb.md                      comandos de preparação e diagnóstico
```

## Build

Requisitos: Android Studio (ou JDK 17), Android SDK 35. `minSdk` 26.

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/`.

O workflow `.github/workflows/build-release.yml` gera o APK de release assinado a cada push na `main`. A keystore e as senhas ficam em GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) e nunca no repositório.

## Preparando um tablet

O Device Owner só pode ser aplicado em um aparelho sem conta Google, sem perfil de trabalho e sem bloqueio de tela. Com a depuração USB ativa:

```bash
adb install -r GestorEscolar.apk
adb shell dpm set-device-owner com.escola.tabletmanager/.SchoolDeviceAdminReceiver
```

Para preparar vários tablets sem usar terminal, use o [configurador para Windows](GestorEscolarTool/). Os demais comandos estão em [docs/comandos-adb.md](docs/comandos-adb.md).

> Device Owner dá controle total sobre o aparelho e só sai com `dpm remove-active-admin` ou reset de fábrica. Teste cada versão em um tablet separado antes de distribuir.
