# Gestão de Tablets — código Android

Código-fonte do aplicativo Android reutilizável para administração de tablets em instituições de ensino.

## Recursos

- modos aluno e administrador;
- seleção de aplicativos permitidos;
- proteção por senha administrativa;
- integração com `DevicePolicyManager`;
- suporte a `Device Owner`;
- serviço de acessibilidade para proteção de configurações;
- atualização do aplicativo por versão publicada.

## Estrutura

| Caminho | Finalidade |
| --- | --- |
| `app/` | código-fonte e recursos do aplicativo |
| `gradle/wrapper/` | Gradle Wrapper |
| `build.gradle` | configuração principal do Gradle |
| `settings.gradle` | módulos do projeto |
| `COMANDOS_ADB_PRONTOS.txt` | comandos auxiliares de administração |
| `LEIA_PRIMEIRO.txt` | instruções operacionais |

## Abrir e compilar

1. abra o projeto no Android Studio;
2. aguarde a sincronização do Gradle;
3. selecione `Build > Build APK(s)`;
4. localize o APK gerado em `app/build/outputs/apk/`.

## Preparação do tablet

Para configurar o aplicativo como `Device Owner`, use um dispositivo institucional sem conta Google, perfil de trabalho ou bloqueio de tela. Ative a depuração USB e autorize o computador.

```powershell
adb devices
adb shell dpm list-owners
```

> O modo `Device Owner` concede privilégios administrativos. Teste as mudanças em um aparelho de homologação antes de distribuir uma nova versão.


