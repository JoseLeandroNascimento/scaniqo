# Premium no Google Play

O módulo usa Play Billing 9.1.0 e o produto de compra única não consumível `scaniqo_premium`.
Nunca consome a compra. Não oferece assinatura, aluguel ou pré-venda. O preço e o token da oferta
vêm do Google Play; não há preço de produção fixado no código.

## Configuração necessária

1. No Play Console do aplicativo `com.joseleandro.scaniqo`, criar e ativar o produto
   `scaniqo_premium`, com opção de compra permanente e os preços/regiões desejados.
2. Copiar a **chave pública RSA de licenciamento do Google Play**, não o SHA-1 do certificado
   Android nem o ID OAuth do Drive.
3. Definir `scaniqo.play.licenseKey=CHAVE_PUBLICA_BASE64` nas propriedades Gradle locais ou no CI.
   O módulo app a fornece como recurso `scaniqo_play_license_key`.
4. Publicar o AAB assinado no teste interno, configurar testadores de licença e instalar a versão
   pela conta da Play Store autorizada a testar. O produto precisa pertencer ao mesmo aplicativo.

Sem uma chave RSA válida, o checkout fica desabilitado. Nenhuma compra é iniciada automaticamente
por inicialização, restauração, retry ou retorno do aplicativo ao primeiro plano.

## Integração

Incluir `core.billing.di.billingModule` no Koin. Ele fornece `PlayBillingManager` e o alias
`PremiumEntitlementRepository`. Chamar `refresh()` no retorno ao primeiro plano, inclusive após
a interface de pagamento. A tela Premium coleta `manager.state`, mostra `offer.formattedPrice`
e chama `purchase(activity)`, `restore()` ou `retry()`.

Existe um cliente por processo. `close()` serve para teardown; não fechar no `Activity.onStop`,
pois a própria interface de checkout pausa a Activity. A referência da Activity não é retida.

## Verificação e armazenamento

O gerenciador só libera acesso após validar a assinatura SHA1withRSA sobre os bytes originais
do recibo, a chave RSA, o pacote, o produto, o estado concluído, o token e a data. Estado pendente,
callback OK sem recibo e sucesso ao abrir o checkout nunca concedem acesso.

Recibos concluídos e confirmados por acknowledgement são guardados em arquivo privado
`noBackupFilesDir`, por gravação atômica. Cada carregamento verifica novamente a assinatura.
Não existe boolean Premium persistido. Uma consulta bem-sucedida sem compra válida remove o
cache e revoga acesso, inclusive depois de reembolso/revogação ou mudança da conta Play.

Se o Google Play estiver indisponível, um recibo já validado pode manter acesso por no máximo
sete dias desde a última confirmação. Relógio anterior à confirmação, cache expirado/corrompido
e chave ausente falham sem conceder acesso. Recibos sem acknowledgement não são persistidos:
o app tenta novamente após 2, 10, 30 e 120 segundos, e em cada retorno ao primeiro plano.

**Limite da arquitetura atual:** a assinatura é verificada no dispositivo. Sem servidor e RTDN,
o app não recebe revogações enquanto está offline nem consegue impedir reutilização de um
recibo válido em um dispositivo adulterado. Para proteção central e revogação imediata, a próxima
etapa é validar tokens na Google Play Developer API em backend próprio e consumir RTDN.
A chave pública não é segredo e não substitui essa validação no servidor.

## Validação

Os testes JVM usam pares de chaves gerados em execução, assinam fixtures e verificam adulteração,
chave errada, compra pendente, produto/pacote incorreto, token divergente, cache offline, expiração,
restauração, consulta vazia e retries de acknowledgement. Nunca usam credenciais reais.

Antes de lançar, executar no teste interno com os instrumentos de teste do Play: compra concluída,
cancelamento, pagamento pendente que conclui/cancela, reinstalação e restauração, item já possuído,
rede indisponível, acknowledgement e reembolso/revogação. Esses cenários exigem configuração real
no Play Console e não são substituídos por mocks nem por APK instalado diretamente via adb.

Documentação oficial:
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/security
- https://developer.android.com/google/play/billing/one-time-product-multi-purchase-options-offers
- https://developer.android.com/google/play/billing/release-notes
