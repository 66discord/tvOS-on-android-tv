# tvsOS — launcher estilo tvOS para Fire TV / Android TV

Um launcher (tela inicial) Android escrito em Kotlin, com a linguagem visual do **tvOS**: fundo escuro
com brilho que reage ao foco, ícones grandes com cantos suaves, Top Shelf, tipografia limpa e navegação
100% por controle remoto. Totalmente **personalizável** com salas, cores, grade e destaques.

> **O que isto é (e o que não é):** é um APK independente, feito do zero, inspirado no visual do tvOS.
> Não é firmware, não é uma ROM do Fire OS e não contém código ou assets da Apple. Também não substitui
> arquivos de sistema do Fire TV — ele roda como um app, com intent de `HOME`.

---

## Recursos

| Área | O que tem |
|---|---|
| **Home** | Grade de apps instalados, ícones arredondados, foco com escala + brilho + varredura de luz |
| **Fundo reativo** | O brilho do fundo muda para a cor dominante do ícone em foco (estilo tvOS) |
| **Top Shelf** | Até 6 apps em cards grandes 16:9 com fundo desfocado do próprio ícone |
| **Salas** | Várias "salas", cada uma com um conjunto próprio de apps, trocáveis por pílulas no topo |
| **Personalização** | 7 cores de destaque, 3 estilos de fundo, 4/5/6 colunas, nomes on/off, relógio on/off, animações on/off |
| **Screensaver** | Tela preta com relógio grande depois de 1/3/5/10 min parado |
| **Ações por app** | Segure **OK** em um app: abrir, fixar/desafixar no Top Shelf, ocultar da Home, ver informações |
| **Ajustes** | Painel com barra lateral estilo tvOS: Aparência, Salas, Apps, Top Shelf, Sistema, Sobre |
| **Sem dependências** | Zero AndroidX: só APIs do framework (build muito mais à prova de erro) |

---

## Como é a tela

```
┌───────────────────────────────────────────────────────────────────────┐
│ tvsOS        ( Todos ) ( Sala 1 ) ( Sala 2 )        22:41   [Ajustes] │
│ BOM DIA                                              sábado, 25 de out│
│                                                                       │
│ EM DESTAQUE                                                           │
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                    │
│ │  Netflix     │ │  YouTube     │ │  Plex        │   ← cards 16:9     │
│ └──────────────┘ └──────────────┘ └──────────────┘                    │
│                                                                       │
│ TODOS OS APPS                                                         │
│  ▢   ▢   ▢   ▢   ▢        ← grade com foco que cresce e brilha       │
│  ▢   ▢   ▢   ▢   ▢                                                    │
│                                                                       │
│ OK abrir · segure OK para opções · ≡ ajustes · voltar sai             │
└───────────────────────────────────────────────────────────────────────┘
```

---

## Controles

| Tecla | Ação |
|---|---|
| `◁ ▷ △ ▽` | Navegar |
| `OK` | Abrir o app em foco |
| `OK` (segurar) | Menu de opções do app |
| `≡` (Menu) | Abrir Ajustes |
| `voltar` | Fecha painel → sai da sala/editor → duplo toque sai do tvsOS |

---

## Estrutura

```
app/src/main/java/com/tvsos/launcher/
├── HomeActivity.kt      Home, grade, Top Shelf, salas, screensaver
├── SettingsActivity.kt  Todos os ajustes (aparência, salas, apps, sistema)
├── Views.kt             BackgroundView, TileView, HeroTile, ChipView, SettingRow, NavRow
├── Sheet.kt             Painéis modais (opções, confirmação, renomear)
├── Store.kt             Persistência em SharedPreferences (JSON via org.json)
├── AppsRepo.kt          Lista de apps, ícones em bitmap, cor dominante, intents
├── Model.kt             AppItem, Room
└── Ui.kt                Paleta, helpers de dp/cor/tipografia, blur barato
```

---

## Como gerar o APK

Existem três caminhos. Escolha **um**.

### 1) GitHub Actions (não precisa instalar nada no PC)

1. Crie um repositório **privado** no GitHub e envie esta pasta:

   ```bash
   git init
   git add .
   git commit -m "tvsOS"
   git branch -M main
   git remote add origin https://github.com/SEU_USUARIO/tvsOS.git
   git push -u origin main
   ```

2. No repositório: aba **Actions** → workflow **Build APK** → **Run workflow**.
3. Quando terminar, abra a execução e baixe o artifact **tvsOS-apk** (um `.zip` com o `.apk` dentro).
4. Descompacte e siga `INSTALAR-FIRESTICK.md`.

O workflow instala JDK 17 + Android SDK 34 + Gradle 8.2 sozinho e assina o APK com a chave de debug
(suficiente para sideload).

### 2) Android Studio (mais simples com interface)

1. Instale o **Android Studio** (Hedgehog ou mais novo) com JDK 17.
2. `File → Open` e escolha a pasta `tvsOS android`.
3. Espere o *Gradle sync* terminar (ele baixa o SDK 34 se faltar).
4. `Build → Build Bundle(s)/APK(s) → Build APK(s)`.
5. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`
   (ou `app/build/outputs/apk/release/app-release.apk` com `assembleRelease`).

### 3) Linha de comando

Pré-requisitos: **JDK 17**, **Android SDK** com `platforms;android-34` e `build-tools;34.0.0`,
**Gradle 8.2+** no PATH (o wrapper `.jar` não vem no repositório de propósito).

```bash
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
gradle assembleRelease
# Windows (PowerShell):
#   $env:ANDROID_SDK_ROOT="C:\Users\VOCE\AppData\Local\Android\Sdk"
#   gradle assembleRelease
```

Ou use `build-apk.bat` (Windows), que verifica o Gradle e chama `assembleRelease`.

---

## Instalação no Fire TV Stick

Resumo (detalhes em [`INSTALAR-FIRESTICK.md`](INSTALAR-FIRESTICK.md)):

1. Fire TV → `Configurações → Meu Fire TV → Sobre` → clique 7× no nome do aparelho.
2. Ative **ADB Debugging** e **Apps de fontes desconhecidas**.
3. No PC: `adb connect 192.168.0.XX:5555` e `adb install -r tvsOS.apk`.
4. O app aparece em **Seus apps** como `tvsOS`.

### Sobre virar a "Home" oficial

O APK declara `android.intent.category.HOME`, então **em Android TV / Google TV** ele aparece na
escolha de launcher e pode ser definido como padrão (`Ajustes → Sistema → Definir como Home`).

No **Fire OS** a Amazon normalmente ignora/bloqueia launchers de terceiros. O que costuma funcionar:

```bash
adb shell cmd package set-home-activity com.tvsos.launcher/.HomeActivity
```

Se retornar `SecurityException`, o Fire OS da sua versão não permite e não há como contornar sem root
ou sem um gerenciador de launcher específico (que usa os mesmos comandos). Nesse caso o tvsOS abre
como app normal, por cima da Home da Amazon — o app continua 100% funcional.

---

## Personalizando

Tudo em **Ajustes** (tecla `≡`):

- **Aparência** — cor de destaque, estilo de fundo, colunas (4/5/6), nomes dos apps, relógio,
  Top Shelf on/off, animações, tempo do screensaver.
- **Salas** — criar, renomear, escolher apps e excluir. A sala ativa define o que aparece na Home.
  Sala sem nenhum app escolhido mostra todos.
- **Apps** — ocultar/mostrar qualquer app (nada é desinstalado).
- **Top Shelf** — até 6 apps fixados nos cards grandes.
- **Sistema** — abrir a tela de Home do sistema, configurações, recarregar lista, resetar tudo.

Atalho rápido: na Home, **segure OK** em qualquer app para fixar/ocultar/abrir informações.

---

## Limites conhecidos

- Não é um sistema operacional: sem troca de firmware, sem root, sem alterar apps da Amazon.
- O Fire OS decide se aceita o tvsOS como Home (veja acima).
- Ícones são lidos do sistema — apps que não expõem ícone caem no ícone padrão do tvsOS.
- O "desfoque" dos cards do Top Shelf é um blur barato por escala (sem RenderScript), para funcionar
  em Fire OS antigo sem dependências.
- `targetSdk 28` proposital: evita restrições de visibilidade de pacotes e de armazenamento em Fire OS
  antigo, e é o que permite listar todos os apps sem pedir permissão extra em tempo de execução.

---

## Licença e créditos

Código próprio, sem assets de terceiros. "tvOS", "Apple TV" e "Fire TV" pertencem aos seus donos;
este projeto é um exercício independente de design de interface, sem afiliação.
