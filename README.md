# BTC SMA 200 para Android

Aplicativo de código aberto (MIT) para acompanhar BTC/USD na Kraken, com SMA de 200 candles **diários ou semanais** e notificações quando um candle **fechar** acima ou abaixo da média. Android 8 ou superior. Não precisa de conta na corretora, chave de API, Node.js ou servidor próprio.

## O que esta versão faz

- Gráfico dos últimos 120 fechamentos e da respectiva SMA 200.
- Alertas independentes para diário e semanal, inicialmente desativados.
- Histórico local dos últimos 50 cruzamentos.
- Notificações locais e botão de teste.
- Consulta manual e agendamento em segundo plano a cada 15 minutos.

**Limite importante:** o Android pode adiar as consultas por economia de bateria. Não há garantia de aviso instantâneo ou de execução com o aplicativo forçado a parar. Abra novamente depois de forçar parada. Para entrega independente do celular e com menor atraso, uma evolução do projeto deve monitorar os candles em um servidor e enviar push.

## Colocar no seu GitHub pessoal

1. Entre em [github.com/new](https://github.com/new), com sua conta pessoal.
2. Use o nome `btc-sma-android`, escolha **Public** para compartilhar o código e crie o repositório **sem adicionar README, licença ou .gitignore**. Esses arquivos já estão nesta pasta.
3. Abra o terminal **dentro desta pasta** e execute, substituindo `SEU_USUARIO` pelo seu usuário do GitHub:

```bash
git init -b main
git add .
git commit -m "Adiciona app Android BTC USD com SMA 200 e alertas"
git remote add origin https://github.com/SEU_USUARIO/btc-sma-android.git
git push -u origin main
```

Se o Git pedir nome e email, configure os dados da sua conta pessoal antes do commit:

```bash
git config user.name "Seu nome"
git config user.email "Seu email pessoal ou email privado noreply do GitHub"
```

Autentique o push pelo gerenciador de credenciais do Git ou pelo GitHub CLI (`gh auth login`). O GitHub não aceita a senha da conta como senha de Git HTTPS. Não coloque tokens, senhas nem chaves de assinatura dentro dos arquivos do projeto.

## Gerar e instalar o primeiro APK pelo navegador

1. Depois do push, abra **Actions → Gerar APK Android** no repositório.
2. Se o GitHub pedir, habilite os workflows. O primeiro build inicia no push; para repetir, use **Run workflow** na branch `main`.
3. Aguarde a execução terminar com sucesso. Abra a execução e baixe o artefato **btc-sma-apk-teste**. É necessário estar autenticado no GitHub para baixar artefatos.
4. No Android, extraia o ZIP e abra `app-debug.apk`. Quando solicitado, permita que o navegador ou gerenciador de arquivos instale aplicativos dessa origem.
5. Abra **BTC SMA 200**, permita notificações e ative os alertas diário e/ou semanal. Toque em **Testar notificação**.

O APK de teste serve para experimentar. A assinatura de debug dos runners pode mudar entre builds; uma nova instalação pode exigir desinstalar o APK anterior, apagando o histórico local. Para atualizações que preservem os dados e download direto sem ZIP, use a Release assinada abaixo.

## Publicar um APK em Releases

Você precisará de uma chave de assinatura **sua e permanente**. Guarde uma cópia segura: sem a mesma chave, não é possível atualizar uma instalação existente. Os passos seguintes são executados no seu computador, com um JDK instalado (o Android Studio também inclui um).

```bash
mkdir -p ~/.android
keytool -genkeypair -v -keystore ~/.android/btc-sma-release.jks \
  -alias btc-sma -keyalg RSA -keysize 3072 -validity 10000
```

No repositório, vá a **Settings → Secrets and variables → Actions → New repository secret** e crie:

| Secret | Conteúdo |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Conteúdo da chave em Base64, obtido pelo comando abaixo |
| `ANDROID_KEYSTORE_PASSWORD` | Senha escolhida para o keystore |
| `ANDROID_KEY_ALIAS` | `btc-sma` |
| `ANDROID_KEY_PASSWORD` | Senha da chave; normalmente igual à do keystore para PKCS12 |

```bash
base64 -w0 ~/.android/btc-sma-release.jks
```

Cole a saída **somente no campo do secret**, nunca em um arquivo ou issue pública. Depois, crie e envie a tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Em **Actions → Preparar APK para Release → Run workflow**, informe `v0.1.0`. O workflow compila, testa e prepara um **rascunho** em **Releases**, com `BTC-SMA-200.apk` assinado. Revise o rascunho e clique em **Publish release**.

Depois de publicado, qualquer pessoa com acesso ao repositório poderá abrir esta URL pelo navegador do celular e baixar o APK diretamente:

```text
https://github.com/SEU_USUARIO/btc-sma-android/releases/latest/download/BTC-SMA-200.apk
```

Nas próximas versões, incremente `versionCode` e `versionName` em `app/build.gradle`, faça commit e use uma nova tag. Conserve os mesmos secrets de assinatura. O APK de Release não atualiza o APK de debug: desinstale a versão de teste antes da primeira Release.

## Como os sinais são calculados

- Fonte: endpoint público `OHLC` da Kraken, par `XBTUSD` (BTC/USD), intervalos `1440` e `10080` minutos. É USD, não USDT.
- O último candle retornado pela Kraken ainda está em formação e é sempre excluído.
- Cada SMA é a média aritmética dos 200 fechamentos, **incluindo o fechamento do próprio candle avaliado**.
- O cruzamento compara a posição do fechamento em relação à SMA daquele candle com a posição do fechamento anterior em relação à sua própria SMA.
- Um toque exato na média conserva o último lado conhecido; tocar e voltar não é cruzamento.
- A primeira consulta após ativar um alerta estabelece a referência sem enviar sinais antigos.
- Nas consultas seguintes, processamos os fechamentos posteriores à referência. Cada sinal é registrado uma única vez por período e candle.
- A API fornece até 720 candles. Se houver uma lacuna grande que impeça reconstruir a SMA, retomamos a referência e mostramos um aviso, sem inventar cruzamentos no intervalo ausente.
- Os horários exibidos são UTC e o agrupamento diário/semanal segue os candles da Kraken. Valores e fechamentos podem diferir de outras corretoras.
- O histórico registra a hora do fechamento e a hora da detecção. A entrega da notificação depende da permissão do Android; sinais detectados sem permissão não são reenviados depois.

## Abrir no Android Studio

Abra esta pasta como projeto Gradle. Use JDK 17, Android SDK 35, Build Tools 35.0.0 e Gradle 8.11.1. O projeto fixa AGP 8.9.2 e não inclui binários de Gradle Wrapper. A automação instala o Gradle necessário.

Com Gradle 8.11.1 disponível no terminal:

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. Os testes cobrem a janela de 200 períodos, ambos os sentidos de cruzamento, toque na média, repetição, primeira consulta, recuperação semanal, lacunas e respostas antigas.

## Dados e privacidade

Preferências, candles calculados e sinais ficam no armazenamento privado do aplicativo; não há login, analytics ou chave de corretora. As consultas HTTPS são feitas diretamente à Kraken, que recebe o IP de origem. Desinstalar o app apaga os dados locais. O código não envia ordens de compra ou venda.

## Referências

- [Kraken: contrato OHLC e exclusão do candle em formação](https://docs.kraken.com/api-reference/market-data/get-ohlc-data)
- [Android: agendamento periódico com JobInfo](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long))
- [Android: permissão de notificações](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [GitHub: baixar artefatos de Actions](https://docs.github.com/en/actions/managing-workflow-runs/downloading-workflow-artifacts)
- [GitHub: criar Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
