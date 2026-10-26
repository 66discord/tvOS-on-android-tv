# Instalar o tvsOS no Fire TV Stick

Tempo estimado: 10 a 15 minutos. Você vai precisar de:

- Um PC (Windows, macOS ou Linux) na **mesma rede Wi-Fi** do Fire TV
- O APK do tvsOS (`tvsOS.apk` ou `app-release.apk`)
- Opcional: cabo USB OTG + ADB, se preferir instalar com fio

---

## 1. Liberar o Fire TV para instalação

No controle, vá em:

```
Configurações  →  Meu Fire TV  →  Sobre
```

Clique **7 vezes seguidas** no nome do aparelho (ex.: "Fire TV Stick 4K").
Aparece a mensagem *"Você agora é um desenvolvedor"*.

Volte um nível e entre em **Opções do desenvolvedor**:

- **Depuração ADB** → LIGADO
- **Depurar apps da Amazon** → pode deixar desligado
- **Instalar apps de fontes desconhecidas** → LIGADO

> Em Fire OS mais antigos o caminho é `Configurações → Sistema → Opções do desenvolvedor`.

---

## 2. Descobrir o IP do Fire TV

```
Configurações  →  Meu Fire TV  →  Sobre  →  Rede
```

Anote algo como `192.168.0.42`.

---

## 3. Instalar por ADB (recomendado)

### Instalar o ADB no PC

- **Windows:** baixe o [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)
  e descompacte; ou instale o Android Studio.
- **macOS:** `brew install android-platform-tools`
- **Linux:** `sudo apt install android-tools-adb`

### Conectar e instalar

```bash
adb connect 192.168.0.42:5555
adb devices
# deve listar: 192.168.0.42:5555    device
```

Na primeira conexão o Fire TV mostra um diálogo **"Permitir depuração?"** — marque
"Remember this computer" e confirme.

```bash
adb install -r tvsOS.apk
```

Saída esperada: `Success`.

Se o Fire TV estiver em Fire OS 7 e recusar com `INSTALL_FAILED_...`, tente:

```bash
adb shell pm install -r -d /data/local/tmp/tvsOS.apk
```

(para isso, envie o arquivo antes: `adb push tvsOS.apk /data/local/tmp/`)

---

## 4. Instalar sem PC (app Downloader)

1. Na Fire TV, abra a busca e instale o **Downloader** (ícone laranja, da AFTVnews).
2. Coloque o `tvsOS.apk` em um link direto acessível — por exemplo, anexe o APK em uma
   **Release** do seu repositório no GitHub e copie a URL do arquivo
   (`https://github.com/USUARIO/REPO/releases/download/v1/tvsOS.apk`).
3. No Downloader, digite a URL → **Download** → **Install**.
4. Confirme o aviso de "app de fonte desconhecida".

---

## 5. Abrir o tvsOS

O app aparece em **Seus apps** (ou na linha de apps da Home) com o nome **tvsOS**.

Ao abrir pela primeira vez ele lista os apps instalados e já monta a grade.

---

## 6. Tentar definir como Home

O tvsOS declara a categoria `HOME`, então o sistema pode oferecê-lo como tela inicial.

### Android TV / Google TV

`Ajustes → Sistema → Definir como Home` (dentro do tvsOS) ou
`Configurações → Apps → App padrão → Tela inicial`.

### Fire TV

Tente pelo PC:

```bash
adb shell cmd package set-home-activity com.tvsos.launcher/.HomeActivity
```

- **Sem erro** → pressione o botão Home do controle: deve abrir o tvsOS.
- **`SecurityException` / `not allowed`** → sua versão do Fire OS bloqueia launchers de terceiros.
  Não existe contorno sem root. O tvsOS continua funcionando normalmente como app.

Para reverter (voltar à Home da Amazon):

```bash
adb shell cmd package set-home-activity com.amazon.firehomestarter/.MainActivity
```

> O nome do pacote da Home da Amazon varia por versão. Se o comando acima falhar, use
> `adb shell cmd package resolve-activity -c android.intent.category.HOME` para descobrir.

---

## 7. Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `adb: no devices/emulators found` | IP errado, outra rede ou ADB desligado | Confira o IP, mesma rede, ADB ligado |
| `connection refused` | Depuração ADB desligada ou Fire TV dormindo | Acorde o Fire TV e religue o ADB |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK assinado por outra máquina | `adb uninstall com.tvsos.launcher` e instale de novo |
| `INSTALL_FAILED_OLDER_SDK` | Fire OS muito antigo (Android < 5.0) | Não suportado (minSdk 21) |
| Abre e fecha sozinho | Conflito de assinatura ou versão de build | Reinstale limpo e veja `adb logcat | grep tvsOS` |
| Não aparecem apps na grade | Nenhum app com intent de launcher | Instale algum app e use "Recarregar lista" nos Ajustes |
| Home continua abrindo a Amazon | Fire OS bloqueia launcher de terceiros | Use o tvsOS como app (item 6) |

Logs úteis:

```bash
adb logcat -s AndroidRuntime:E tvsOS:V
```

---

## 8. Desinstalar

```bash
adb uninstall com.tvsos.launcher
```

Ou: `Configurações → Aplicativos → Gerenciar apps instalados → tvsOS → Desinstalar`.
