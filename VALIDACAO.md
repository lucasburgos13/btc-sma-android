# Validação desta entrega

- 10 testes JUnit executados com sucesso em JDK 17, usando o código real de cálculo e detecção de cruzamentos.
- Todos os fontes Java compilados com `javac --release 17` contra `android.jar` da plataforma Android 35. Foi usado um `R` temporário para a referência ao ícone, que o Gradle gera no build normal.
- Manifesto e ícone validados como XML; dois workflows validados como YAML.
- Endpoint público semanal da Kraken consultado com sucesso: retornou mais de 200 candles fechados, suficientes para a SMA 200 semanal.

Ainda não foram executados o build completo do APK com Gradle, o Android Lint nem testes em emulador/aparelho. O workflow `Gerar APK Android` está configurado para executar build, testes e lint quando o código for enviado ao GitHub.

## Verificar no aparelho antes de usar os alertas

1. Instalar o APK, permitir notificações e testar a notificação pelo botão.
2. Carregar diário e semanal, conferir a data de fechamento UTC e comparar a SMA com BTC/USD da Kraken no mesmo agrupamento de candles.
3. Ativar cada alerta e confirmar que o primeiro carregamento não dispara sinais históricos.
4. Fechar o app sem forçar parada e verificar que o horário da consulta avança após a execução do agendamento. O Android pode atrasá-lo.
5. Testar sem internet: deve manter os valores anteriores com mensagem de falha na próxima consulta executada.
6. Após reiniciar o celular, verificar a retomada do agendamento com os alertas ativos.

Sem aguardar um cruzamento real, os dois sentidos e a prevenção de duplicação são validados pelos testes automatizados do cálculo. O botão de teste verifica apenas a entrega local de notificação.
