# NEXUS IA (Android)

App Android em Kotlin com login, detecção de IAs instaladas, permissão Shizuku/Termux e interface flutuante.

## Gerar o APK debug no GitHub
1. Crie um repositório novo no github.com e envie todo o conteúdo desta pasta (branch `main`).
2. Abra a aba **Actions** e rode **Build APK Debug** (roda sozinho a cada push).
3. Quando terminar, baixe o arquivo **NexusIA-debug-apk** no fim da página da execução e instale o `app-debug.apk`.

## Como usar no celular
1. Entre com nome e senha (o ADM tem acesso às pastas por IA).
2. Conceda **Shizuku** ou **Termux**. Ao liberar uma delas, o app pede a **sobreposição de tela** e a **acessibilidade**.
3. Com tudo liberado, a bolinha com o **N** aparece. Toque nela para abrir o painel, arraste pelo título para mover.
4. **Mostrar chat** desenha uma moldura vermelha no campo de texto da IA aberta.
5. **Enviar texto** lista os `.txt` da pasta da IA atual (e da pasta "Geral") e preenche o chat.
6. A engrenagem pequena embaixo aumenta, diminui ou fecha a interface (com confirmação Sim/Não).

## Pastas por IA (ADM)
No login ADM, **Pastas por IA** deixa escolher uma pasta com arquivos `.txt` para cada IA detectada e uma pasta "Geral".
Você escreve os seus próprios textos nessas pastas; o app só envia o que estiver nelas.

## IAs reconhecidas
ChatGPT, Gemini, Claude, Copilot, DeepSeek, Grok, Perplexity, Poe, Le Chat, Character.AI, Kimi e Doubao.
Para incluir outra, adicione o pacote em `Core.kt` (AiDetector), em `AndroidManifest.xml` (queries) e em `res/xml/accessibility_config.xml`.
