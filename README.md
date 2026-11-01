# NEXUS IA (Android)

App Android em Kotlin com login, detecção de IAs instaladas, permissão Shizuku/Termux e interface flutuante.

## Gerar o APK debug no GitHub
1. Crie um repositório novo no github.com e envie todo o conteúdo desta pasta (branch `main`).
2. Abra a aba **Actions** e rode **Build APK Debug** (roda sozinho a cada push).
3. Quando terminar, baixe o arquivo **NexusIA-debug-apk** no fim da página da execução e instale o `app-debug.apk`.

## Como usar no celular
1. Entre com nome e senha (o ADM tem acesso às pastas por IA).
2. Conceda **Shizuku** ou **Termux**. Ao liberar uma delas, o app pede a **sobreposição de tela** e a **acessibilidade**.
3. Com tudo liberado, a bolinha com o **N** aparece. Toque nela para abrir o menu quadrado; arraste pela barra de cima (N + título) para mover. O **−** minimiza.
4. O menu mostra a **IA detectada** (app ou site) e tem 4 botões:
   - **CHAT**: liga/desliga a moldura vermelha no campo de texto da IA aberta (a etiqueta mostra o nome da IA).
   - **TEXTOS**: lista os `.txt` da pasta da IA atual (e da pasta "Geral") e preenche o chat. `‹` volta.
   - **TAMANHO**: alterna 80% / 100% / 120% / 140%.
   - **FECHAR**: encerra a interface (com confirmação Sim/Não).

Se a acessibilidade aparecer cinza e não ligar (Android 13+, app instalado por APK): Info do app → ⋮ → **Permitir configurações restritas**.

## Pastas por IA (ADM)
No login ADM, **Pastas por IA** deixa escolher uma pasta com arquivos `.txt` para cada IA detectada e uma pasta "Geral".
Você escreve os seus próprios textos nessas pastas; o app só envia o que estiver nelas.

## IAs reconhecidas
ChatGPT, Gemini, Claude, Copilot, DeepSeek, Grok, Perplexity, Poe, Le Chat, Character.AI, Kimi e Doubao (apps),
mais Qwen, Meta AI, Z.ai e HuggingChat (só pelo site). Os sites também são detectados no Chrome, Samsung Internet, Firefox, Brave, Edge, Opera, Kiwi e DuckDuckGo
(o NEXUS lê só a barra de endereço desses navegadores).

Para incluir outra IA, adicione uma linha em `Ai.kt` (`AiDetector.KNOWN`, com pacote e/ou domínio). Se ela tiver app, repita o pacote em `AndroidManifest.xml` (queries) e em `res/xml/accessibility_config.xml`.

## Organização do código
- `Ai.kt` lista de IAs e detecção · `Perms.kt` permissões · `Store.kt` login e preferências
- `FloatingService.kt` janelas flutuantes · `FloatingMenu.kt` menu quadrado · `NexusAccessibilityService.kt` leitura do chat
- `MainActivity.kt` telas do app · `Ui.kt` / `Views.kt` estilos e views
