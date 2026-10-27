# Plano de evolução das ferramentas do Scaniqo

**Status:** implementação das ferramentas visíveis realizada em 24/10/2026. A especificação abaixo permanece como referência; a seção final registra o que foi validado e os limites conhecidos.

**Objetivo:** tornar as ferramentas mais bonitas, claras e interativas, com animações que expliquem as operações e um fluxo consistente para salvar PDFs novos, permitindo escolher **nome e tag no bottom sheet existente**.

**Base:** código atual do módulo :app, incluindo as melhorias já aceitas de compressão e mesclagem. Antes de implementar, conferir alterações posteriores e preservar o trabalho do usuário.

## 1. Resultado esperado

A maioria das ferramentas deve funcionar como uma jornada em **quatro etapas distintas**:

**1. Escolher arquivo no app ou aparelho → 2. Abrir a ferramenta e ajustar → 3. Aplicar com animação personalizada → 4. Salvar com nome e tag, quando fizer sentido.**

A escolha do arquivo tem uma tela própria, bonita e simples. Só depois de uma fonte válida a interface de configuração/edição aparece. Ao tocar na ação principal, a ferramenta apresenta seu estado de trabalho com animação específica. Ao concluir a geração de um PDF novo, abre o bottom sheet existente para nome e tag.

Essa separação é um requisito de experiência, não apenas uma descrição do fluxo: não apresentar seleção de origem, todos os ajustes, processamento e metadados juntos numa tela longa. As exceções de importação, ZIP e edição de documento existente estão definidas na seção 4.

A interface deve mostrar a tarefa da etapa atual. Explicações, opções avançadas e ações secundárias aparecem apenas quando necessárias. A animação representa o trabalho real; ela nunca determina se uma operação terminou.

Entregáveis da implementação:

- Central de ferramentas mais leve, mantendo busca e categorias.
- Etapa inicial dedicada à escolha de arquivos do app ou aparelho, com apresentação bonita, seleção discreta e comportamento consistente.
- Segunda etapa focada em configurar/editar, sem repetir o seletor inicial; transições e retorno preservam o trabalho.
- Animações coerentes de seleção, processamento, erro e conclusão.
- Extração e compressão integradas ao bottom sheet de salvamento.
- Mesclagem integrada visualmente ao mesmo padrão, preservando o trabalho recente.
- Ajustes nos controles de editar, assinar e preencher, mantendo a precisão do editor.
- Estados acessíveis, tratamento de cancelamento e restauração, previews e testes dos fluxos alterados.

## 2. Escopo e limites

### Ferramentas atualmente visíveis

O catálogo explícito em [ToolsHubScreen.kt](../app/src/main/java/com/joseleandro/scaniqo/ui/toolsHub/ToolsHubScreen.kt) contém:

| Ferramenta | Tipo de saída atual | Direção |
|---|---|---|
| Importar | PDF novo na biblioteca, após importação comum | Preservar seletor Android e bottom sheet; melhorar feedback |
| Mesclar | PDF novo quando iniciada pela central | Preservar seleção/reordenação recentes; harmonizar processamento e salvar |
| Extrair páginas | PDF novo, hoje cadastrado imediatamente | Gerar rascunho e pedir nome/tag antes do cadastro |
| PDF para imagens | ZIP exportado pelo seletor Android | Melhorar seleção, formato, destino e feedback |
| Comprimir | PDF novo quando há redução, hoje cadastrado imediatamente | Exibir medidas reais e pedir nome/tag antes do cadastro |
| Editar PDF | Alteração do documento existente | Melhorar controles e estados sem mudar a semântica de salvar |
| Assinar | Alteração no workspace do documento existente | Melhorar seleção e controles, preservando posicionamento preciso |
| Preencher | Alteração no workspace do documento existente | Melhorar interação contextual, teclado e feedback |

### Decisões de escopo

1. **Nome e tag são obrigatoriamente oferecidos para PDFs novos gerados pelas ferramentas visíveis.** A tag continua opcional, incluindo “Sem tag”.
2. **Editar um documento existente continua atualizando esse documento.** Não transformar cada assinatura, preenchimento ou ajuste em uma cópia. Aplicar uma edição ao workspace e salvar o documento são etapas diferentes.
3. **PDF para imagens continua exportando ZIP.** O nome do arquivo e o destino são escolhidos pelo seletor Android. Tags pertencem à biblioteca de documentos; não criar um PDF fictício nem uma tag que não será persistida para o ZIP.
4. **Importação como fonte de ferramenta é um fluxo distinto:** PdfImportViewModel.saveForTool hoje cadastra a fonte automaticamente, antes de entrar na ferramenta. Preservar esse comportamento nesta entrega e garantir o bottom sheet para a saída gerada. Não introduzir dois pedidos consecutivos de nome/tag. Uma futura mudança para fontes temporárias exige escopo próprio.
5. **Não reativar recursos ocultos:** IMAGES_TO_PDF, ID_CARD e RECEIPT não fazem parte da central atual; ANNOTATIONS, REDACTION e WATERMARK são filtrados na barra do editor.
6. Existe um caminho interno de “salvar cópia censurada” que cadastra outra saída diretamente, mas pertence à ferramenta oculta. Não anunciar que todos os caminhos internos do aplicativo foram migrados. Registrar essa dívida para quando o recurso voltar a ser exposto.
7. Não trocar o motor de compressão, a biblioteca de PDF, as versões de dependências, o banco ou a biblioteca de navegação apenas para realizar esta reformulação. Ajustar destinos, argumentos e estado da navegação existente é permitido quando necessário para separar as etapas.
8. Preservar as alterações aceitas em DocumentMergeIllustration, componentes de mesclagem, PdfCompressionWorkspace e otimizadores PDF.

## 3. Diagnóstico do código atual

| Área | Implementação encontrada | Consequência para o plano |
|---|---|---|
| Central | Grade adaptativa, busca, categorias e feedback de pressão; cards com altura mínima de 184dp | Reduzir densidade e espaço excessivo sem cortar texto grande |
| Seletores | EditorDocumentPicker no ToolsHub, DocumentSignaturePicker, seletor em PdfUtilityRoute e DocumentMergeLibrarySheet | Compor uma etapa inicial comum de origem e reaproveitar os seletores; não abrir ajustes antes de selecionar |
| Extração/imagens | PdfUtilityWorkspace já tem miniaturas, intervalos e seleção de páginas | Evoluir essa interação, evitando refazer um seletor existente |
| Compressão | PdfCompressionWorkspace já tem animação, níveis e resultado antes/depois | Aproveitar a base; separar “gerado” de “salvo” |
| Mesclagem | Ilustração animada, biblioteca em sheet, seleção discreta e reordenação | Refinar consistência e transições, preservando regras e acessibilidade |
| Salvar | DocumentMetadataSheet já contém nome, tag, validação, retry e proteção durante gravação | Reutilizar o componente e seu controlador |
| Utilities | PdfUtilityViewModel.compress/extract usam commitPrepared e cadastram imediatamente | Separar preparação do arquivo da confirmação do cadastro |
| Navegação | A rota PdfUtilityTool não apresenta DocumentActionOverlays; seleção/configuração ainda convivem no mesmo workspace de utilities | Separar etapas na sessão da ferramenta e garantir sheet ao finalizar, sem saída prematura da rota |
| Retorno do salvar | DocumentCreationResult tem documentId e tagId, sem origem/operação | Correlacionar a criação à ferramenta correta |
| Edição | DocumentToolsViewModel.save termina em replaceContent | Preservar atualização do original e distinção de cópia nova |

Arquivos de referência:

- [PdfUtilityViewModel.kt](../app/src/main/java/com/joseleandro/scaniqo/ui/pdfTools/PdfUtilityViewModel.kt)
- [PdfUtilityRoute.kt](../app/src/main/java/com/joseleandro/scaniqo/ui/pdfTools/PdfUtilityRoute.kt)
- [PdfUtilityWorkspace.kt](../app/src/main/java/com/joseleandro/scaniqo/ui/pdfTools/PdfUtilityWorkspace.kt)
- [PdfCompressionWorkspace.kt](../app/src/main/java/com/joseleandro/scaniqo/ui/pdfTools/PdfCompressionWorkspace.kt)
- [DocumentMetadataSheet.kt](../app/src/main/java/com/joseleandro/scaniqo/ui/documentCommon/components/DocumentMetadataSheet.kt)
- [DocumentActionsViewModel.kt](../app/src/main/java/com/joseleandro/scaniqo/ui/documentCommon/DocumentActionsViewModel.kt)
- [AppNavigation.kt](../app/src/main/java/com/joseleandro/scaniqo/core/navigation/AppNavigation.kt)

## 4. Jornada em quatro etapas e direção visual

### 4.1 Linguagem visual comum

Usar a metáfora de **folhas que se organizam e se transformam**, já presente na mesclagem e compressão. Manter MaterialTheme, ScaniqoTheme, formas e cores existentes.

- Fundo com superfícies do tema, cartões discretos e uma cor de destaque por contexto.
- Ícones e ilustrações vetoriais/Canvas no padrão existente; não adicionar dependência de animação.
- Espaçamento baseado em MaterialTheme.dimensions: 16dp como margem inicial, 8dp entre elementos relacionados e 24dp entre grupos, ajustados ao espaço.
- Título curto e, quando necessário, uma frase auxiliar. Não repetir a mesma explicação em título, descrição e rodapé.
- Uma ação principal por etapa. Usar rótulos concretos como “Extrair 3 páginas”, “Comprimir PDF” e “Mesclar 3 PDFs”, em vez de repetir “Aplicar” em todos os contextos.
- Opções secundárias como botões de texto, chips ou menu; ícones isolados com descrição acessível.
- Indicação de etapa discreta e opcional, como “Escolher arquivo” e “Ajustar”; evitar um stepper grande ou etapas futuras clicáveis.
- Cabeçalho e cor da ferramenta acompanham a jornada. Ao avançar, uma transição curta mantém a sensação de continuidade.
- A separação das etapas deve continuar em telas grandes; usar o espaço para ampliar preview e controles, sem juntar novamente todos os passos.

### 4.2 Etapa 1 — Escolher arquivo

**Objetivo:** escolher a origem e o documento sem ver ainda os parâmetros da ferramenta.

Apresentar uma tela inicial dedicada com o nome da ferramenta, ilustração contextual de folhas e duas opções fáceis de reconhecer:

~~~text
‹  Comprimir PDF

       [ilustração de folhas]

       Escolha seu PDF
 [Do Scaniqo]       [Do aparelho]
~~~

- “Do Scaniqo” abre a biblioteca com busca, miniaturas, nome e páginas. “Do aparelho” abre o seletor Android filtrado para PDFs.
- Ambas as origens ficam visíveis, com importância equivalente. Discrição significa poucos elementos, não esconder uma origem num menu difícil de descobrir.
- A seleção na biblioteca pode reutilizar os bottom sheets existentes. A tela inicial fornece o contexto; não criar outra lista duplicada nem uma nova tela de upload.
- Lista com linhas compactas, seleção por check/semântica e alvos de 48dp. Nomes completos disponíveis à acessibilidade.
- Biblioteca vazia apresenta “Escolher do aparelho”; falha de leitura mantém “Tentar novamente”. Um erro da biblioteca não bloqueia o dispositivo.
- Em seleção única, tocar num documento válido abre a etapa 2, sem botão “Continuar” redundante.
- Em mesclagem, permitir acumular PDFs das duas origens e mostrar contador discreto. “Continuar com N PDFs” abre a etapa 2 quando a seleção resolvida tem pelo menos dois itens.
- Importação/leitura de metadados/desbloqueio necessários são subestados desta etapa, com feedback próprio. Só avançar quando a fonte puder ser entregue à ferramenta; nunca usar uma animação para simular prontidão.
- Cancelar o seletor Android ou a biblioteca retorna à escolha de origem, sem erro e sem perder seleção anterior da mesma sessão.
- Ao entrar por uma ação sobre um documento já aberto/selecionado, validar o contexto e abrir diretamente a etapa 2. Não pedir novamente o mesmo arquivo.
- Arquivo inválido, indisponível ou sem permissão mantém a etapa de seleção com ação de trocar/repetir.
- Não pedir nome/tag da saída nem iniciar a transformação nesta etapa. A importação de fonte segue a ressalva de escopo da seção 2.

### 4.3 Etapa 2 — Usar a ferramenta

**Objetivo:** definir, alterar e revisar o efeito antes de aplicar.

~~~text
‹  Extrair páginas

[miniatura] Contrato.pdf · 12 páginas   [Trocar]

Páginas                         3 selecionadas
[1 ✓] [2] [3 ✓] [4 ✓] ...
[Todas] [Limpar] [Intervalo]

                    [Extrair 3 páginas]
~~~

- Abrir somente após fonte válida, ou a partir do documento atual já validado.
- Mostrar o documento e apenas os controles próprios da ferramenta: páginas, ordem, compressão, assinatura, preenchimento etc.
- A origem vira um resumo compacto com “Trocar”. Não repetir os dois cards de origem nem o hero da etapa 1.
- Na mesclagem, esta é a tela de revisar/reordenar/remover PDFs. “Adicionar PDF” retorna ao seletor como edição da seleção, preservando os itens existentes.
- A ação principal fica junto ao rodapé e só habilita com configuração válida. Preview e controles devem caber/rolar sem disputa de gestos.
- Nome/tag ficam para a etapa final dos PDFs novos.
- Não transformar cada toque num ajuste em processamento de tela inteira. Mudanças de opção e preview recebem microinterações; a etapa 3 começa ao executar a ação principal.
- Editor/assinatura/preenchimento mantêm a página estável durante toque, escrita, zoom e posicionamento.

### 4.4 Etapa 3 — Aplicar com animação da ferramenta

**Objetivo:** comunicar que a ação escolhida está sendo executada, com identidade visual própria.

- Ao aplicar, transicionar para uma superfície de processamento dedicada à operação, substituindo os controles editáveis enquanto ela trabalha.
- Usar ilustração central, título curto como “Extraindo páginas…” e apenas informações reais úteis. Não deixar um formulário editável atrás de um spinner como representação principal.
- A estrutura pode ser compartilhada, mas cada ferramenta tem desenho/movimento próprios: folhas se unindo, folhas selecionadas se separando, pilha se compactando, páginas virando imagens, traço sendo aplicado ou campos sendo preenchidos.
- Sem tempo mínimo artificial, porcentagem inventada ou animação longa obrigatória. Se o trabalho terminar rapidamente, a transição pode ser breve.
- Botão/gesto de cancelar existe apenas quando a operação real suporta cancelamento seguro. Confirmar gravação segue as proteções já existentes.
- Bloquear novo disparo, troca de fonte e mudanças de parâmetros incompatíveis enquanto processa.
- Em erro recuperável, retornar à etapa 2 com fonte/opções preservadas e ação de repetir. Erro da fonte pode encaminhar à etapa 1.
- Cancelar processamento retorna à configuração após limpar a saída não entregue. Não abrir o sheet de salvar para trabalho cancelado.
- A conclusão real conduz à etapa 4; compressão sem ganho conduz a um resultado informativo sem sheet.
- Nas operações locais instantâneas do editor, usar feedback curto de aplicação em vez de fingir trabalho demorado. A animação de processamento acompanha apenas trabalho real de aplicar/renderizar/gravar e nunca muda a posição do conteúdo.

### 4.5 Etapa 4 — Salvar e concluir

**Objetivo:** decidir os metadados do resultado quando houver um PDF novo para a biblioteca.

- Ao concluir a geração, retirar o estado ocupado e abrir automaticamente DocumentMetadataSheet, uma única vez, sobre um resumo compacto do resultado.
- Nome sugerido editável, tag da origem/contexto como sugestão e opção “Sem tag”.
- No caso de compressão, o resumo usa tamanho antes/depois real; em extração/mesclagem, pode mostrar a quantidade real de páginas resultantes.
- Não inserir um botão intermediário “Continuar” apenas para abrir o formulário.
- Salvar executa o cadastro; só depois exibir “Salvo” e “Abrir PDF”.
- Dispensar o sheet significa descartar a saída preparada, segundo o contrato de limpeza, e voltar à etapa 2 com fonte/opções preservadas.
- Falha de salvar permanece no sheet com nome/tag e rascunho intactos para repetir.
- Após sucesso, permitir abrir, voltar às ferramentas ou iniciar outra operação; nova operação não reaproveita callbacks/resultados antigos.

### 4.6 Como a jornada se aplica a cada ferramenta

| Ferramenta | Escolher | Ajustar | Aplicar/animação | Concluir |
|---|---|---|---|---|
| Extrair | Um PDF do app/aparelho | Páginas e ordem | Folhas escolhidas formam uma cópia | Sheet nome/tag |
| Comprimir | Um PDF do app/aparelho | Nível de compressão | Pilha se compacta | Sheet nome/tag se houver ganho; informação se não houver |
| Mesclar pela central | Vários PDFs de uma ou ambas as origens | Revisar e ordenar | Folhas convergem | Sheet nome/tag |
| PDF para imagens | Um PDF do app/aparelho | Páginas e formato | Folhas viram imagens durante exportação | ZIP no destino escolhido; sem tag de biblioteca |
| Editar | Um PDF, ou documento atual | Ferramentas do editor | Feedback próprio da edição/salvamento real | Salvar alterações no mesmo documento |
| Assinar | Um PDF, ou documento atual | Escolher/posicionar assinatura | Traço/aplicação, sem mover a assinatura | Aplicar ao workspace e salvar alterações |
| Preencher | Um PDF, ou documento atual | Texto e marcações | Campos/checks se completam, fora do conteúdo real | Aplicar ao workspace e salvar alterações |
| Importar | Arquivo do aparelho pelo seletor Android | Não inventar configuração sem função | Arquivo entrando durante preparação | Sheet nome/tag |

**Exceções deliberadas:**

- **Importar:** já é uma ação de origem, portanto pode abrir diretamente o seletor Android. Não oferecer importar novamente um arquivo que já está na biblioteca nem criar uma etapa 2 vazia.
- **PDF para imagens:** o SAF precisa fornecer destino antes da escrita. Na ação “Exportar imagens”, pedir nome/destino pelo seletor Android; se cancelar, permanecer na etapa 2; se confirmar, iniciar a etapa 3 e concluir após escrita. Não preparar um segundo ZIP temporário apenas para forçar uma ordem visual.
- **Editar/assinar/preencher e mesclar dentro do editor:** o documento existente mantém ID/nome/tag. Aplicar ao workspace não equivale a persistir. Exibir “Aplicado” quando apenas o rascunho mudou; “Salvo” somente após gravação. Metadados continuam acessíveis pela ação existente de editar nome/tag; não obrigar um formulário a cada ajuste.
- **Compressão sem ganho:** termina com resultado honesto e opção de ajustar/voltar, sem salvar cópia redundante.
- **Documento já conhecido:** pular apenas a seleção repetida, sem pular revisão/configuração nem confirmação de cadastro quando houver saída nova.

### 4.7 Voltar, trocar arquivo e manter a sessão

- Voltar na etapa 1 retorna à origem da navegação; se isso encerrar uma sessão com edições, preservar a confirmação de saída existente. Fechar um seletor interno apenas fecha esse seletor.
- Quando a entrada veio da central, Voltar na etapa 2 retorna à etapa 1 da mesma sessão com fonte/opções preservadas. Mostrar a fonte atual e “Retomar ajustes”; não avançar automaticamente só porque ainda existe uma seleção. Na primeira seleção única não há botão de continuar redundante.
- Quando a entrada veio do documento já aberto, Voltar na etapa 2 retorna a esse documento seguindo as regras de aplicar/descartar do editor. Não introduzir uma tela de origem que não fazia parte desse caminho.
- “Trocar arquivo” abre a etapa 1 em modo substituição. Cancelar conserva fonte, opções e rascunho atuais. Só substituir após outra fonte válida e confirmação de descarte, se houver edições a perder.
- Troca confirmada invalida páginas selecionadas, preview/resultado anterior, destino ZIP e solicitações de seletor associadas à fonte antiga; restabelecer apenas preferências compatíveis. Selecionar a mesma fonte não deve apagar ajustes.
- Em mesclagem, adicionar fontes preserva ordem/itens anteriores e evita duplicatas; voltar da revisão permite editar a seleção sem começar outra operação. O documento atual do editor continua obrigatório.
- Restaurar a etapa correta ao girar a tela/retornar do seletor. Identidade da sessão não muda em recomposição nem em transição entre selecionar e configurar.
- Vincular callbacks de seletores à sessão e à solicitação de origem. Ignorar retorno obsoleto sem aplicá-lo a outra ferramenta.
- Fonte importada deve abrir a etapa 2 da ferramenta solicitada apenas uma vez, sem empilhar rotas duplicadas.
- Preferir um estado de jornada dentro do destino existente quando suficiente. Se separar destinos, transportar identidade/referência da fonte e manter o mesmo proprietário da operação.
- Não encerrar a sessão de mesclagem rápida antes da decisão de salvar: preparar → sheet no contexto da mesclagem → salvar/descartar → resultado/configuração. Isso altera o retorno automático atual e exige validar host, entrega e navegação juntos.

### 4.8 Organização da implementação da jornada

- ToolsHubScreen e AppNavigation encaminham a ferramenta e, quando existir, a referência do documento atual.
- PdfUtilityRoute coordena seleção/configuração/processamento/resultado das utilities com seu ViewModel; os workspaces apresentam os controles somente na etapa 2.
- DocumentMergeRoute/Destination preservam o estado de seleção entre as etapas 1 e 2; DocumentQuickActionsHost coordena entrega sem encerrar a sessão antes do resultado de salvar/descartar.
- Rotas de editor/assinatura reutilizam o workspace e a sessão existentes. O fluxo comum adapta a entrada, sem criar outra cópia do estado de edição.
- Extrair componentes visuais reutilizáveis para tela de origem e conteúdo de processamento quando houver compartilhamento real. A estrutura visual é comum; ilustração, texto, ação e contrato de seleção variam por ferramenta.
- Manter operações nos repositórios/ViewModels atuais. Não criar um controlador universal que copie todos os estados de importação, utility, merge e editor.
- Adicionar/ajustar previews para cada etapa, além das variantes de tema/tamanho. Os nomes de novos arquivos devem seguir a organização do projeto.

## 5. Especificação por tela

### 5.1 Central de ferramentas

**Arquivos:** toolsHub/ToolsHubScreen.kt; recursos de strings e tema quando necessários.

- Preservar as oito entradas, busca e filtros atuais. Tocar numa ferramenta dependente de PDF abre sua etapa 1 de escolha; não abrir diretamente um workspace vazio cheio de controles.
- Cards com ícone, título e no máximo um subtítulo curto que explique o resultado.
- Reduzir altura mínima excessiva após verificar nomes traduzidos e fonte ampliada.
- Grade adaptativa: duas ou mais colunas conforme espaço; aceitar uma coluna com fonte grande/janela estreita.
- Animar a reorganização ao filtrar e o feedback de toque; usar chaves estáveis.
- Estado de busca vazia com mensagem curta e ação de limpar filtro.
- Não animar continuamente todos os cards.
- Preservar enabled/canStartDocumentAction para impedir abertura concorrente de operações.

**Aceite:** todas as ferramentas continuam encontráveis; textos não cortam em 320dp/fonte 1.5; filtros não perdem estado ao retornar.

### 5.2 Importar

**Arquivos:** documents/components/PdfImportDialogs.kt, PdfImportViewModel.kt e platform/PdfImportHandler.kt.

- Manter o seletor nativo do Android.
- Ao retornar, usar feedback de arquivo entrando na biblioteca durante a preparação real.
- Mensagens curtas para processamento, senha e erro, com ação de repetir quando possível.
- Preservar cancelar enquanto a operação permite e bloquear interrupção apenas na etapa de confirmação já protegida.
- Importação comum termina no DocumentMetadataSheet existente.
- Não criar uma tela intermediária com novo formulário de upload.

**Aceite:** cancelar o seletor não cria rascunho nem erro falso; nome/tag continuam editáveis antes do cadastro da importação comum.

### 5.3 Mesclar

**Arquivos:** documentMerge/DocumentMergeScreen.kt, DocumentMergeDestination.kt, DocumentMergeRoute.kt; documentCommon/components/DocumentMergeSelectionContent.kt, DocumentMergeSelectionRow.kt, DocumentMergeLibrarySheet.kt, DocumentMergeIllustration.kt; documents/platform/DocumentQuickActionsHost.kt; documentEditor/components/DocumentMergeDialogs.kt.

- Aproveitar a animação de folhas convergindo e a seleção discreta já implementadas.
- Etapa 1: escolher vários PDFs pela biblioteca/dispositivo, combinar as duas origens e continuar quando houver pelo menos dois itens válidos.
- Etapa 2: revisar a lista ordenável e os controles existentes; mostrar ordem, contador e ações discretas para adicionar/remover. Não colocar toda a revisão na tela inicial.
- Animar entrada/remoção/reordenação; não disputar posição com o arrasto em andamento.
- Manter arrastar com auto-scroll e alternativa acessível “Mover para cima/baixo”.
- Preservar o documento atual do editor como item não removível, quando aplicável.
- Processamento real usa convergência de folhas; sem porcentagem inventada.
- A mesclagem rápida já entrega rascunho ao formulário comum: preservar a entrega única e o salvamento posterior.
- Etapa 3 usa o processamento animado; etapa 4 abre o sheet no contexto da mesclagem. Adiar o retorno automático atual à tela anterior: ao descartar, retornar à revisão; após salvar, oferecer abrir/voltar. Preservar o protocolo de entrega única durante essa mudança.
- Mesclagem dentro do editor continua alterando o workspace e usando o fluxo de salvar alterações.

**Aceite:** ordem final corresponde à ordem escolhida; excluir item da biblioteca durante seleção é tratado; cancelar não cria documento; melhorias recentes continuam funcionando.

### 5.4 Extrair páginas

**Arquivos:** pdfTools/PdfUtilityWorkspace.kt, PdfUtilityRoute.kt e PdfUtilityViewModel.kt.

- Etapa 1 escolhe um PDF. Somente na etapa 2 mostrar a grade de páginas como conteúdo principal.
- Manter seleção total, limpar, intervalos e prévia ampliada; intervalo fica em seção secundária expansível.
- Mostrar quantidade selecionada junto à ação, com pluralização.
- Preservar a ordem produzida pelo parser/seleção; não ordenar silenciosamente as páginas.
- Origem e importação pertencem à etapa 1. Na etapa 2 manter apenas resumo compacto da fonte e “Trocar”.
- Remover nome antecipado e a condição name.isNotBlank() da habilitação de gerar.
- Usar uma rolagem principal previsível; rever a grade interna limitada a 360dp para evitar disputa de gestos.
- Ao executar, abrir a etapa 3 com folhas selecionadas formando uma nova pilha; não manter os controles ativos durante a geração.
- Gerar rascunho, abrir sheet de nome/tag e cadastrar somente após Salvar.
- Após salvar, apresentar confirmação curta e “Abrir PDF”; evitar navegação disparada por resultado antigo.

**Aceite:** seleção vazia ou intervalo inválido não executa; teclado não cobre ação; cancelar o sheet mantém fonte/opções e não deixa PDF novo cadastrado.

### 5.5 PDF para imagens

**Arquivos:** pdfTools/PdfUtilityWorkspace.kt e PdfUtilityRoute.kt.

- Etapa 1 escolhe o PDF; etapa 2 reutiliza o padrão de seleção de páginas de Extrair.
- Formato JPG/PNG em controle compacto com rótulos; explicar diferença apenas em ajuda contextual curta quando necessário.
- Deixar claro que a saída contém imagens em um ZIP.
- Ao tocar em “Exportar imagens”, abrir o seletor Android de nome/destino antes da escrita. Cancelar mantém a etapa 2; confirmar dispara a etapa 3 uma única vez, sem exigir um segundo toque. O callback atual apenas atribui targetUri: adaptar a entrega para executar a solicitação confirmada, vinculando fonte, páginas, formato e identidade da tentativa.
- Caso um destino válido já tenha sido escolhido na sessão, mostrá-lo de forma compacta com opção de trocar. Nova fonte ou nova sessão invalida esse destino; não reutilizar callback antigo.
- Durante exportação, animar folhas se transformando em cartões de imagem.
- Mostrar conclusão somente após escrita bem-sucedida; oferecer nova exportação sem reutilizar callback antigo.
- Preservar tratamento de senha, cancelamento do seletor e falha ao gravar.
- Não aplicar tag de biblioteca a ZIP externo.

**Aceite:** formato, páginas e destino correspondem à escolha; cancelar SAF não exporta; falha não aparece como conclusão.

### 5.6 Comprimir

**Arquivos:** pdfTools/PdfCompressionWorkspace.kt, PdfUtilityWorkspace.kt, PdfUtilityRoute.kt e PdfUtilityViewModel.kt.

- Separar a escolha da fonte em etapa 1. Evoluir o layout existente para etapa 2 com documento compacto e duas opções, etapa 3 com animação de compressão e etapa 4 com comparação real antes/depois e sheet.
- Remover “Nome da cópia” antecipado quando o sheet assumir os metadados.
- Manter senha contextual para PDFs protegidos.
- Usar animação de folhas se aproximando/reduzindo volume enquanto a operação real estiver ativa.
- Mostrar redução com bytes e percentual calculados do resultado real.
- Com ganho: preparar a saída e abrir sheet com nome sugerido e tag da origem; resumo pode aparecer de forma compacta no sheet.
- Enquanto aguarda confirmação, o arquivo está “pronto para salvar”; não exibir “Salvo” nem habilitar “Abrir”.
- Após confirmação, associar o ID criado ao resumo e oferecer “Abrir PDF”.
- Sem ganho: informar “Este PDF já está otimizado” ou equivalente fiel ao resultado; não abrir sheet nem criar cópia redundante.
- Preservar proteção, tratamento de arquivos incompatíveis e integridade do original. Não prometer redução de todo PDF nem uma porcentagem mínima.
- Não alterar os otimizadores PDF como parte da reformulação visual.

**Aceite:** resultado sem ganho não cria documento; cancelamento do sheet não deixa cópia; original e chave de proteção permanecem intactos.

### 5.7 Editar PDF e ferramentas internas visíveis

**Arquivos:** documentEditor/DocumentViewerScreen.kt, DocumentEditorToolScreen.kt, DocumentEditorToolRoute.kt; components/DocumentToolsBar.kt, DocumentToolScreenLayout.kt e DocumentFiltersBar.kt.

- Pela central, selecionar o arquivo na etapa 1 e só então abrir o editor na etapa 2; a partir de um documento atual, validar o contexto e dispensar nova seleção.
- Preservar a página como foco principal.
- Reutilizar DocumentToolScreenLayout, incluindo controles recolhíveis em janela baixa e disposição lateral em paisagem.
- Harmonizar seleção da ferramenta, expansão de controles, feedback de aplicação e botão de salvar.
- Layout/correções: agrupar ajustes relacionados e mostrar apenas controles da operação selecionada.
- Páginas: manter seleção e reordenação claras, com feedback de posição e alternativa acessível.
- Filtros/qualidade: destacar opção atual e preview; não recalcular ou renderizar páginas a cada frame de animação.
- Proteção: campos contextuais e erros claros; preservar regras de senha e armazenamento seguro.
- Não animar posição/escala da página enquanto o usuário recorta, ajusta, arrasta ou amplia.
- “Aplicar” confirma o rascunho da ferramenta; “Salvar alterações” confirma o documento. Não trocar esses significados.
- Preservar confirmação de saída com mudanças e o filtro que oculta ferramentas descontinuadas.

**Aceite:** nenhuma animação reinicia zoom, seleção, workspace ou rascunho; salvar continua atualizando o documento correto.

### 5.8 Assinar

**Arquivos:** documents/components/DocumentSignaturePicker.kt; documentSignature/DocumentSignatureScreen.kt e seus controles/painéis existentes.

- Usar a etapa 1 comum de origem/arquivo e abrir a etapa 2 de assinatura após a fonte estar disponível. Ao entrar pelo documento atual, pular a seleção repetida.
- Biblioteca e ajustes aparecem conforme a etapa; reduzir instruções permanentes.
- Animar entrada da assinatura escolhida e destaque da seleção uma única vez.
- Preservar mover, redimensionar, girar, recortar, lupa e zoom.
- Confirmar deve continuar acessível em paisagem e fonte ampliada.
- Animação de traço decorativo apenas no estado vazio/processamento, sem mover a assinatura durante posicionamento.
- Ao confirmar assinatura, mostrar feedback de aplicação/traço conforme duração real; ao gravar o documento, mostrar o processamento real. Não anunciar “Salvo” ao apenas alterar o workspace.
- Preservar edição do documento existente e o salvamento posterior do workspace.

**Aceite:** posição final, escala e rotação não mudam ao abrir controles, girar o dispositivo ou concluir a animação.

### 5.9 Preencher

**Arquivos:** documentEditor/DocumentEditorToolScreen.kt e componentes DocumentOverlayEditor/DocumentFillMarkEditor e controles associados.

- Etapa 1 escolhe o PDF, salvo quando já existe um documento atual válido. Etapa 2 abre o canvas com inserção contextual de texto e marcações.
- Dar feedback curto a inserir, selecionar, duplicar e aplicar. A representação de campos/checks durante processamento deve ficar na superfície de status, sem deslocar ou modificar visualmente os campos que o usuário editou.
- Alternância de texto/check e propriedades com transições discretas.
- Priorizar teclado, foco e visibilidade do campo ativo.
- Preservar segundo toque para editar, commit ao trocar elemento, autoajuste de texto e isolamento por página/workspace.
- Não substituir o editor por formulário longo nem adicionar animação à posição do campo sob edição.

**Aceite:** teclado não cobre o campo/ação; trocar elemento não perde texto; animações não removem foco nem alteram geometria.

## 6. Fluxo compartilhado de salvar PDFs novos

### 6.1 Componente a reutilizar

Reutilizar DocumentMetadataSheet através do fluxo DocumentActionOverlays → DocumentOverlays. DocumentActionsViewModel deve continuar controlando nome, tag, validação, gravação e descarte.

O sheet já possui comportamentos importantes que devem permanecer:

- Nome obrigatório, aparado e limitado por DOCUMENT_NAME_MAX_LENGTH.
- Tag opcional e opção “Sem tag”.
- Carregamento, erro e tentativa novamente das tags.
- Bloqueio de salvar com tag ainda não validada; opção sem tag pode continuar conforme regra existente.
- Campos e rascunho preservados quando a gravação falha.
- Bloqueio de cliques duplicados e de fechamento durante a confirmação.
- Rolagem/IME e mensagens acessíveis.
- Limpeza do rascunho ao cancelar, sem apagar arquivo já persistido.

Refinamento visual permitido: cabeçalho curto, miniatura/ícone discreto, resumo opcional do resultado e animação leve no botão. Evitar nova versão do formulário com validações duplicadas.

### 6.2 Estados e transições

Nomes abaixo descrevem o contrato; adaptar os nomes Kotlin ao padrão existente. A etapa visual deve ser explícita no estado da sessão, e não inferida apenas de document == null ou de um campo ocupado.

| Etapa/estado | Conteúdo/ação | Saída esperada |
|---|---|---|
| 1 — Escolhendo fonte | Tela de origem, biblioteca ou seletor Android | Fonte válida → 2; cancelar seletor → manter 1 |
| 1 — Preparando fonte | Leitura/importação/desbloqueio necessários | Fonte pronta → 2; erro → repetir/trocar |
| 2 — Configurando | Documento + controles exclusivos da ferramenta | Aplicar se válido → 3 |
| 2 — Escolhendo destino ZIP | Seletor Android vinculado à solicitação | Confirmar → 3; cancelar → 2 |
| 3 — Processando | Superfície dedicada e animação específica | Rascunho → 4; sem ganho → resultado; erro → recuperação |
| 4 — Aguardando metadados | Sheet com nome sugerido e tag | Salvar ou descartar |
| 4 — Salvando | Sheet bloqueado para edição/fechamento | Salvo ou erro recuperável no sheet |
| Concluído | Confirmação e “Abrir PDF” quando aplicável | Abrir, voltar ou iniciar nova sessão |
| Sem ganho | Informação honesta de compressão | Ajustar na etapa 2 ou voltar |
| Erro | Feedback na etapa responsável | Repetir sem duplicar e sem perder configuração |
| Descartado | Limpeza concluída e resultado consumido | Voltar à etapa 2 com fonte/opções preservadas |

A navegação segue transições de estado válidas. Não gerar ao selecionar fonte, salvar ao terminar uma animação ou iniciar outra operação ao recompor a tela. A confirmação no sheet é o único disparo de cadastro para os caminhos migrados.

Separar a identidade da sessão da ferramenta da identidade de cada tentativa de executar/trocar fonte. Isso permite retornar da etapa 4 à 2 e aplicar novamente, rejeitando callbacks atrasados da tentativa anterior. Na implementação, evoluir operationId/operationGeneration existentes; evitar duas máquinas de estado concorrentes no composable e no ViewModel.

### 6.3 Separar geração de persistência

- Extrair/comprimir devem produzir ScannedDocument e informações do resultado sem cadastrar imediatamente.
- Substituir, para esses caminhos, o uso direto de commitPrepared por entrega explícita ao controlador de salvar.
- Tirar o nome dos pré-requisitos da geração; validar nome no sheet antes da persistência.
- Usar sugestão localizada de nome, respeitando o limite e permitindo edição.
- Extração/compressão sugerem a tag da fonte. Mesclagem rápida mantém a tag padrão do contexto atual. O usuário pode trocar/remover.
- Não usar ID fictício para representar saída preparada.
- Atualizar PdfCompressionSummary para distinguir resultado preparado, sem ganho e salvo.
- Não deixar dois caminhos de persistência ativos para a mesma saída.

### 6.4 Identidade e propriedade do rascunho

Implementar um contrato pequeno de entrega contendo identidade da operação, origem da ferramenta, ScannedDocument, nome sugerido e tag sugerida. Adotar o formato de estado/eventos já usado no projeto.

Regras obrigatórias:

1. O produtor continua responsável pelo temporário até o controlador aceitar a entrega.
2. A aceitação deve ser explícita e idempotente por operação/URI. Reentrega não substitui nome/tag já digitados.
3. Após aceite, apenas DocumentActionsViewModel salva ou descarta o rascunho.
4. Se outro formulário estiver ativo, não sobrescrevê-lo. Bloquear nova ação ou manter a entrega pendente até liberação.
5. Resultado de uma operação antiga não pode abrir sheet, navegar ou modificar resumo de uma operação nova.
6. Ao cancelar antes da entrega, descartar apenas a saída temporária pertencente ao produtor.
7. Ao cancelar o sheet, usar discardScan. Se a limpeza falhar, preservar a referência e oferecer retry, como já ocorre. Após limpeza concluída, entregar um resultado de descarte correlacionado à operação, consumido uma única vez: a ferramenta sai de “aguardando metadados”, limpa o resumo preparado e volta à configuração, mantendo fonte/opções. Não emitir descarte bem-sucedido enquanto a limpeza falhar.
8. Após commit no banco, cancelamento/limpeza não pode apagar o arquivo salvo.
9. Preservar as garantias de idempotência por URI e proteção de referências em DocumentRepositoryImpl.
10. Enquanto a etapa 4 possui uma saída preparada, impedir troca de fonte/ajustes por baixo do sheet. Primeiro concluir salvar/descarte; só então voltar à configuração ou iniciar nova tentativa.

Não criar um segundo repositório de arquivos nem uma nova tabela apenas para esse fluxo.

### 6.5 Host e entrega do resultado

**Arquivos:** AppNavigation.kt, DocumentActionsHandler.kt, DocumentActionEvent.kt, DocumentCreationResult.kt, DocumentActionsUiState.kt e estado de metadados.

- Garantir que o sheet esteja visível em PdfUtilityTool. Hoje a simples emissão dos eventos não basta.
- Preferir um host comum único dos overlays de metadados, coordenado em AppNavigation; ao centralizar, retirar chamadas locais equivalentes de Documents/Tools/Viewer.
- DocumentActionOverlays também apresenta DocumentDeleteDialog. Ao mover ou separar o host, preservar a confirmação de exclusão e seus handlers, exibidos uma única vez. Não remover as chamadas locais sem dar destino à parte de exclusão.
- Preservar diálogos específicos de scanner, senha, importação e edição. Centralizar o formulário não significa mover todos os diálogos.
- Durante transições de navegação, garantir uma única instância visível do sheet.
- Acrescentar origem/identidade ao resultado de criação, com compatibilidade para scanner/importação existentes.
- Atualizar a biblioteca com documentId e tagId verdadeiros. Eliminar a passagem fixa de null no retorno de utility quando a criação tem tag.
- Entregar o resultado à ferramenta correspondente e reconhecer o consumo uma única vez.
- Se a rota original não estiver ativa, atualizar a biblioteca sem navegar inesperadamente.
- Para compressão/extração, manter confirmação compacta e “Abrir PDF” após salvar; não abrir automaticamente por um evento antigo.
- Adaptar o retorno da mesclagem rápida ao fluxo da seção 4.7: manter a sessão até salvar/descartar, sem navegar para trás assim que o arquivo é preparado. Preservar entrega única e testar retorno, cancelamento e abertura do resultado.

### 6.6 Ciclo de vida e segurança do estado

- Preservar operação, origem e campos necessários à restauração via SavedStateHandle onde apropriado.
- Reutilizar o armazenamento do rascunho já presente em DocumentActionsViewModel; não duplicar sua propriedade no ViewModel da ferramenta.
- Persistir referências e metadados pequenos, nunca bytes/bitmaps nem senhas.
- Manter protectionKeyId sem alteração ao entregar o rascunho.
- Na rotação, restaurar também a etapa visual e a seleção; não voltar automaticamente ao início, gerar novamente nem abrir dois formulários.
- Na morte do processo, validar se o temporário ainda existe; restaurar o sheet quando viável. Se o processamento foi interrompido, retornar a estado recuperável, sem spinner eterno.
- Arquivos não entregues devem seguir política explícita de limpeza de temporários; não apagar temporários ainda referenciados por um rascunho restaurável.
- Respeitar cancelamento e limpeza de credenciais ao ir para background; não interpretar todo ON_STOP como abandono, pois pode haver seletor externo aberto.
- Ajustar beginOperation para não zerar resultado/identidade pertencentes a um rascunho já entregue.
- Preservar comportamento seguro do commit quando a tela encerra durante gravação.

## 7. Sistema de animações

Usar Compose e os desenhos existentes. Os tempos abaixo são parâmetros iniciais de design, a verificar no dispositivo, não requisitos de atrasar operações.

| Interação | Movimento proposto | Duração inicial/condição |
|---|---|---|
| Pressionar card/botão | Pequena escala/cor/elevação | 120–180ms |
| Selecionar página/documento | Borda/check e breve escala | 150–220ms |
| Mostrar opções | Expansão e fade | 180–250ms |
| Mudar estado principal | Transição curta sem salto de layout | 220–350ms |
| Adicionar/remover/reordenar | Animação de item com chave estável | Spring discreto; arrasto controla a posição ativa |
| Entrada da ferramenta | Ilustração específica + transição dos cards de origem | Curta; estática ao repouso |
| Avançar de escolher para ajustar | Resumo da fonte permanece, controles entram | 180–300ms, sem bloquear uso |
| Mesclar | Folhas convergem numa pilha | Enquanto processa |
| Extrair | Folhas escolhidas formam nova pilha | Enquanto processa |
| Comprimir | Pilha diminui de volume | Enquanto processa |
| Exportar imagens | Folhas dão lugar a cartões de imagem | Enquanto exporta |
| Assinar | Traço representativo se completa na superfície de status | Durante trabalho real; não sobre a assinatura posicionada |
| Preencher | Texto/checks representativos surgem na superfície de status | Durante trabalho real; sem simular edição dos campos do usuário |
| Editar | Representação da transformação escolhida: páginas se alinham, giram ou recebem ajuste | Durante trabalho real; feedback curto para aplicação instantânea |
| Importar | Folha entra numa pasta/pilha | Enquanto prepara a fonte/rascunho |
| Salvar | Botão mostra progresso; check ao confirmar | Progresso real, check breve uma vez |
| Erro | Mensagem aparece suavemente | Sem tremer a tela/campo |

Regras:

- Loops decorativos somente quando necessários, visíveis e ligados ao estado real. Fora da tela, parar.
- Respeitar animações reduzidas/desativadas do sistema; o estado estático deve comunicar a mesma informação.
- Sem confetes, flashes, movimento contínuo em todos os cards ou porcentagens simuladas.
- Não impor tempo mínimo de processamento para mostrar a animação.
- Não animar conteúdo pesado de PDF/bitmap por recomposição ampla; manter efeitos locais.
- Não iniciar renderização PDF, I/O ou trabalho de repositório a partir do relógio da animação.
- Usar progresso determinado apenas se houver progresso real disponível; caso contrário, indeterminado.
- Feedback háptico apenas onde útil, como início de reordenação, respeitando preferências do sistema.
- Elementos decorativos fora da árvore de acessibilidade; estados anunciados de forma breve e sem repetição a cada frame.

## 8. Acessibilidade, adaptação e recursos

- Alvos mínimos de 48dp, contraste adequado aos temas existentes e estado selecionado além da cor.
- CTA visível com teclado e barras do sistema; evitar aplicar insets duas vezes.
- Fontes 1.0, 1.5 e 2.0; largura compacta de 320dp; paisagem/janela baixa; tablet.
- Conteúdo rolável quando necessário; não esconder ação porque uma ilustração tomou a área útil.
- Preservar foco, ordem de leitura e labels de campos; não usar placeholder como único rótulo.
- TalkBack deve anunciar ferramenta, documento, quantidade selecionada, erros e resultado; oferecer reordenação sem gesto.
- Navegação por teclado e foco previsível em botões, busca, chips e sheet.
- Texto longo, nomes mistos e RTL: preservar direção de conteúdo e não espelhar indevidamente a ordem numérica das páginas.
- Todas as novas strings em recursos, com plurais quando apropriado.
- Cobrir os locales atuais: base, en, pt, es, fr, hi, in, bn, zh-rCN, ar e ru.
- Preferir mensagens contextualizadas curtas; não remover informações indispensáveis sobre senha, falha, descarte ou efeito da ação.

## 9. Etapas de implementação

### Etapa A — Registrar a base visual e os contratos

**Prioridade:** primeira. **Dependências:** nenhuma.

- Ler instruções locais e a skill change-compose-ui; aplicar skills de preview/adaptação apenas quando a etapa exigir.
- Conferir diff atual e não sobrescrever mudanças aceitas.
- Capturar previews/estado atual de central, merge, compressão, extração/imagens e sheet.
- Confirmar rotas, escopos dos ViewModels, locais de overlays e política de temporários.
- Documentar as quatro etapas, atalhos por documento atual, retorno/troca de fonte e quem possui cada rascunho durante o fluxo.
- Especificar identidade da sessão/tentativa e integração do resultado dos seletores Android sem duplicar destinos.

**Entrega:** inventário confirmado, referência visual das quatro etapas e contrato de seleção/geração/entrega/salvar definido.

### Etapa B — Integrar geração com o bottom sheet

**Prioridade:** alta, antes de remover campos de UI. **Dependências:** A.

- Implementar identidade/origem, aceite de entrega, resultado correlacionado e apresentação única do sheet.
- Migrar compressão e extração para preparar sem persistir.
- Manter sugestão de nome/tag, proteção e caso sem ganho.
- Tratar cancelamento, retry, restauração e operação obsoleta.
- Atualizar testes de ViewModel e controlador compartilhado.
- Verificar regressão em importação e mesclagem.

**Entrega:** fluxo funcional completo com layout ainda próximo do atual.

**Aceite de etapa:** nenhum PDF novo de compressão/extração entra na biblioteca antes de Salvar; nome/tag escolhidos chegam ao banco; cancelar descarta só a saída.

### Etapa C — Criar a etapa de seleção e conectar a jornada

**Prioridade:** alta. **Dependências:** A; pode ocorrer em paralelo com B em arquivos distintos.

- Ajustar cards, densidade, busca e filtros.
- Criar a tela inicial com ilustração da ferramenta e origens “Do Scaniqo”/“Do aparelho”; reutilizar partes visuais e contratos dos seletores existentes.
- Conectar seleção única à abertura da etapa 2 nas utilities/editor/assinatura; mesclagem acumula seleção múltipla e continua para revisão.
- Implementar retorno, cancelamento de troca, documento pré-selecionado e restauração da etapa; preservar permissões/identidade ao voltar do Android.
- Adicionar microinterações e estados vazio/erro/loading coerentes.
- Validar fontes, dark mode, RTL e toque.

**Entrega:** escolha de origem em etapa própria e configuração aberta somente com fonte válida; fluxo direto para documento atual, sem seleção redundante.

### Etapa D — Refinar telas de transformação

**Prioridade:** alta. **Dependências:** B e C.

- Remover campos de nome antecipados e respectivas validações de geração.
- Refazer a etapa 2 de Extrair/Imagens em torno das páginas, retirando dela a escolha inicial de origem.
- Ajustar estados da compressão para preparado/salvo/sem ganho.
- Separar seleção e revisão da mesclagem e adaptar o retorno após gerar para manter a sessão até salvar/descartar; preservar regras e coordenador de entrega.
- Integrar a etapa 3 como superfície de processamento com animação específica por ferramenta e transição para o sheet da etapa 4, sem atrasos artificiais.
- Ajustar feedback de importação e exportação ZIP.

**Entrega:** caminhos principais completos com visual final e animações conectadas ao estado real.

### Etapa E — Refinar editor, assinatura e preenchimento

**Prioridade:** sequência da entrega principal. **Dependências:** A e linguagem visual de C.

- Harmonizar controles e feedback das ferramentas internas visíveis.
- Reutilizar layouts existentes e melhorar estados compactos/paisagem.
- Adicionar animações apenas onde não interferem com edição precisa.
- Preservar aplicar/salvar, proteção, zoom, foco, rascunhos e confirmação de saída; distinguir etapa visual de trabalho real de aplicação instantânea ao workspace.

**Entrega:** experiência consistente também nas ferramentas que alteram documentos existentes.

### Etapa F — Validar e finalizar

**Dependências:** B, C, D e E.

- Executar testes focados, previews e jornadas no dispositivo/emulador.
- Corrigir falhas antes de ampliar a verificação.
- Compilar o aplicativo completo.
- Revisar strings, acessibilidade, animações desativadas e ciclo de vida.
- Registrar o que foi executado e qualquer limitação real; não declarar validação visual só porque o build passou.

**Entrega:** mudanças revisáveis, evidência de funcionamento e checklist de aceite concluído.

## 10. Verificação planejada

### Testes unitários existentes a manter/adaptar

| Arquivo/suíte | Cobertura relevante |
|---|---|
| ui/pdfTools/PdfUtilityViewModelTest.kt | Separação gerar/salvar, sem ganho, operação antiga, proteção, original intacto |
| ui/documentCommon/DocumentActionsViewModelTest.kt | Nome/tag, clique duplo, retry, restauração, entrega repetida, confirmação de consumo |
| ui/documentMerge/DocumentQuickMergeViewModelTest.kt | Entrega única, cancelamento e descarte |
| ui/documentCommon/DocumentMergeSelectionStateTest.kt | Seleção, fontes removidas, ordem e documento atual |
| ui/documents/PdfImportViewModelTest.kt | Importação comum e importação para ferramenta |
| ui/documentEditor/DocumentToolsViewModelTest.kt | Aplicação ao workspace, salvar alterações e preservação da sessão |
| domain/model/PdfPageSelectionTest.kt | Intervalos e ordem de extração |
| ui/documentSignature/*Test.kt | Geometria, viewport e transformações |
| navigation/NavigationViewModelTest.kt | Retorno e estado de navegação quando impactados |

Atualizar expectativas que pressupõem cadastro imediato em utilities. Não apagar testes de integridade apenas porque o momento de persistir mudou.

Casos novos de alto valor:

1. Gerar não chama create; confirmar no sheet chama uma vez com nome/tag escolhidos.
2. Cancelar não cria documento e limpa apenas a saída; o resultado de descarte destrava a ferramenta e permite gerar/salvar novamente, sem reutilizar estado antigo. Falha de limpeza mantém o sheet e permite repetir.
3. Falha ao salvar mantém campos e arquivo; repetir não reprocessa o PDF.
4. Entrega duplicada não redefine metadados editados.
5. Resultado de operação antiga não altera outra ferramenta.
6. Sheet aparece uma vez na rota utility; resultado de scanner/merge não é consumido pela utility.
7. Rotação e restauração mantêm identidade e campos; processamento interrompido não fica ativo para sempre.
8. Tag removida/carregando/com erro segue validação existente.
9. Compressão sem ganho não abre sheet; com ganho só recebe ID após commit.
10. Background e retorno do seletor externo não causam perda indevida ou vazamento de senha.
11. Cancelamento durante commit não apaga arquivo já salvo.
12. Biblioteca recebe a tag real e navegação não ocorre duas vezes.
13. Entrada pela central mostra escolha de origem, sem ajustes da ferramenta; fonte válida abre etapa 2 uma única vez, tanto pelo app quanto pelo aparelho.
14. Cancelar biblioteca/SAF/troca de fonte preserva a sessão; fonte inválida não abre os controles. Documento atual dispensa seleção repetida.
15. Mesclagem combina as duas origens, respeita mínimo de dois itens resolvidos e preserva ordem ao adicionar/voltar; descarte retorna à revisão.
16. Aplicar uma vez abre o processamento correto; alterações e cliques duplicados ficam bloqueados; concluir/cancelar não dispara outra tentativa por recomposição.
17. Para ZIP, confirmar destino inicia exportação; cancelar mantém ajustes e não escreve. Resultado obsoleto de seletor não afeta nova tentativa.
18. Animação desativada ou trabalho instantâneo não impede avançar; operações de editor que apenas mudam workspace não aparecem como documento salvo.

### Previews e validação visual

Para componentes alterados, criar/atualizar previews representativos, sem duplicar todas as combinações em cada componente:

- Claro e escuro; etapa 1 com origens, biblioteca vazia/erro e seleção múltipla.
- Etapa 2 com documento/configuração, sem cards de origem nem hero inicial.
- Processando, erro, aguardando salvar, salvando e salvo.
- Compressão sem ganho.
- 320dp, fonte ampliada, paisagem/janela baixa e largura maior.
- Sheet com nome longo, muitas tags, erro de nome e erro/carregamento de tags.
- Fonte protegida e estado de senha, sem credenciais reais nos previews.

No dispositivo/emulador, verificar teclado aberto, rotação, retorno do seletor, TalkBack, animações desativadas e gestos de reordenação. Preview renderizado e teste instrumental de contexto não substituem esses fluxos.

### Gradle e jornadas

O projeto atual tem apenas o módulo Android :app. Na implementação:

1. Executar testes locais focados primeiro, usando filtros --tests para as suítes afetadas. Exemplo:

   ~~~powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "*PdfUtilityViewModelTest" --tests "*DocumentActionsViewModelTest" --tests "*DocumentQuickMergeViewModelTest" --tests "*PdfImportViewModelTest"
   ~~~

2. Executar os demais testes pertinentes ao editor/navegação se esses caminhos forem alterados.
3. Confirmar consumidores caso a estrutura modular tenha mudado. Hoje :app é o próprio aplicativo.
4. Executar :app:assembleDebug após os testes locais passarem.
5. Havendo dispositivo/emulador, executar testes instrumentados dos fluxos afetados e jornadas manuais. A infraestrutura Compose UI test já está declarada; atualmente há apenas um teste instrumental genérico, que não comprova estes fluxos.
6. Se forem adicionados testes UI, focar os contratos críticos de salvar/cancelar/retry e apresentação única, sem testes que apenas repitam detalhes visuais da implementação.
7. Testes de carga permanecem opt-in; não ampliá-los para cada ajuste visual sem evidência de problema.

Jornada mínima: abrir ferramenta na central → ver etapa 1 → escolher no app/aparelho → configurar na etapa 2 → aplicar e acompanhar animação específica na etapa 3 → escolher nome/tag no sheet da etapa 4 → salvar → abrir → conferir metadados na biblioteca. Repetir com ambas as origens para extrair/comprimir e com origens combinadas para mesclar. Cobrir retorno/troca de fonte, entrada por documento atual, sem ganho, cancelamento, erro de gravação, destino ZIP e edição do mesmo documento.

## 11. Riscos e prevenção

| Risco | Prevenção |
|---|---|
| Seleção continua misturada com controles | Etapa inicial própria e teste de que ajustes só aparecem após fonte válida |
| Trocar/cancelar arquivo apaga ajustes antes da confirmação | Substituição confirmada da fonte, preservando sessão anterior |
| Fonte importada abre rota ou processamento duas vezes | Correlacionar solicitação de seletor e identidade da sessão/tentativa |
| Sheet recebe estado, mas não aparece na utility | Integrar host e validar fluxo de ponta a ponta |
| Dois sheets durante transição de rota | Host coordenado único e teste de apresentação |
| PDF cadastrado antes da confirmação | Remover commit direto dos caminhos migrados |
| Temporário vazando ou arquivo salvo apagado | Propriedade explícita, aceite, discardScan e testes de corrida |
| Nome/tag escolhidos são perdidos | Reentrega idempotente e estado restaurável |
| Salvar com tag, mas biblioteca recebe null | Propagar DocumentCreationResult completo |
| “Salvo” exibido após cancelar | Separar preparado, salvo, descartado e sem ganho |
| Nova operação recebe resultado antigo | Identidade persistível e consumo correlacionado |
| Animação atrapalha edição | Manter geometria da página estável durante gestos |
| Texto reduzido torna ação ambígua | Rótulos curtos, seleção explícita e ajuda contextual |
| UI bonita apenas no preview padrão | Validar fonte grande, teclado, landscape, RTL e dispositivo |
| Refatoração traz recursos descontinuados | Preservar catálogo e filtros explícitos |
| Promessa de comprimir qualquer PDF | Mostrar resultado real e manter tratamento de limites/incompatibilidade |

## 12. Checklist de aceite final

As caixas abaixo permanecem como roteiro de regressão completo; o registro factual da execução e dos limites está na seção 14.

- [ ] As oito ferramentas visíveis continuam acessíveis, sem reativar recursos ocultos.
- [ ] A maioria das ferramentas segue quatro etapas distintas: escolher, ajustar, processar com animação própria e concluir/salvar.
- [ ] Etapa 1 oferece app/aparelho com apresentação bonita; os controles não aparecem antes da fonte válida.
- [ ] Etapa 2 prioriza o documento e a configuração, com origem compacta e ação de aplicar clara.
- [ ] Documento já aberto dispensa nova seleção; trocar/cancelar arquivo e voltar preservam o trabalho conforme o contrato.
- [ ] Mesclagem separa escolha múltipla de revisão/ordem e mantém o contexto até salvar/descartar.
- [ ] Central, origem e controles têm hierarquia clara e menos texto redundante.
- [ ] Mesclagem e compressão preservam as melhorias aceitas.
- [ ] Cada animação tem função e está ligada ao estado real.
- [ ] Não há porcentagem fictícia nem atraso artificial para concluir.
- [ ] Compressão e extração usam DocumentMetadataSheet antes do cadastro.
- [ ] Usuário consegue escolher nome, outra tag ou “Sem tag”.
- [ ] Não existem formulários de salvamento concorrentes.
- [ ] Cancelar não cria cópia nem apaga a origem; após descarte, é possível configurar e gerar outra saída sem reutilizar estado antigo.
- [ ] Centralização dos overlays preserva a confirmação de exclusão existente.
- [ ] Retry de salvar preserva rascunho e metadados.
- [ ] Rotação/reentrega não duplicam documentos nem sheets.
- [ ] Resultado sem ganho não cria documento.
- [ ] ZIP mantém destino/nome pelo Android e não recebe cadastro PDF artificial.
- [ ] Editar/assinar/preencher preservam atualização do documento existente.
- [ ] Fonte ampliada, teclado, modo escuro, RTL e animações desativadas funcionam.
- [ ] Strings novas seguem a localização do app.
- [ ] Testes pertinentes e build passam; jornadas executadas são registradas.
- [ ] Diff final preserva mudanças anteriores e não inclui refatorações fora do escopo.

## 13. Prompt para a IA executora

> Implemente o plano deste documento no Scaniqo, começando pelo estado atual do projeto. Preserve todas as alterações aceitas de compressão e mesclagem e não reative ferramentas ocultas. Leia as instruções locais e as skills aplicáveis antes de editar. Use os recursos do Android Studio para inspecionar e modificar o projeto.
>
> Execute as etapas A–F com entregas verificáveis. A experiência principal deve ter quatro etapas distintas: tela bonita para escolher arquivo do app/aparelho; ferramenta para definir/editar/aplicar; superfície de processamento com animação personalizada; bottom sheet de nome/tag para PDFs novos. Não resolver apenas reorganizando controles numa única tela. Preserve as exceções explícitas para documento atual, importação, ZIP e edição do mesmo documento.
>
> Primeiro defina estado/navegação da jornada e separe geração de PDF e confirmação de cadastro, reutilizando DocumentMetadataSheet e DocumentActionsViewModel. Resolva seleção/troca de fonte, propriedade do rascunho, apresentação única, correlação de resultados, cancelamento e restauração antes de remover os campos antecipados.
>
> Depois refine a central, seletores e telas com menos texto, controles contextuais e animações Compose que representem as operações reais. Preserve a precisão do editor, os temas e a acessibilidade. PDFs novos das ferramentas visíveis devem passar pelo sheet; edição do documento existente e exportação ZIP mantêm suas semânticas.
>
> Valide com testes focados, previews, build e jornadas em dispositivo quando disponível. Continue corrigindo falhas até cumprir os critérios de aceite. Ao concluir, relate mudanças, validações efetivamente executadas e limitações reais. Não considere o trabalho concluído apenas por criar componentes ou animações sem integrá-los ao fluxo real.

## 14. Registro da implementação (24/10/2026)

- As oito entradas da central permanecem visíveis, com busca, categorias, cards mais compactos e etapa de origem própria para editar, assinar e preencher. Extrair, converter em imagens e comprimir usam a mesma escolha app/aparelho antes dos ajustes; mesclar separa seleção múltipla e revisão da ordem.
- As animações de extração, compressão, conversão e mesclagem acompanham o estado real da operação. A extração/exportação usa uma rolagem vertical única com miniaturas carregadas sob demanda. A importação e os controles de edição, assinatura e preenchimento receberam feedback visual sem alterar a geometria de interação do documento.
- Extração, compressão com redução e mesclagem entregam o PDF preparado ao `DocumentMetadataSheet` existente. O cadastro acontece apenas ao confirmar nome e tag; cancelar descarta a saída e conserva a configuração. Compressão sem ganho informa o resultado sem criar cópia. O ZIP continua sendo salvo pelo seletor Android, sem cadastro artificial na biblioteca.
- A sessão evita entregas repetidas, preserva o resultado de salvamento após recriação e bloqueia ações concorrentes durante o sheet. O fluxo de importação não empilha a mesma rota da ferramenta; voltar do editor aberto pela central retorna à escolha de origem.
- Validações: 1.250 testes unitários sem falhas, um teste instrumentado no Samsung A13, `:app:assembleDebug`, previews em tema claro/escuro e em 320 dp com fonte 1,5. No Samsung A13 foram conferidos seleção do app, revisão da mesclagem, retorno com seleção preservada, cancelamento do seletor do aparelho, e extração real até o sheet com descarte sem cadastro. Não foram feitos cadastros de teste na biblioteca pessoal.
- Limites da validação manual: não foram percorridas todas as combinações de PDF protegido, modo paisagem, teclado físico e RTL em dispositivo. A saída preparada é restaurada pelo `SavedStateHandle` e removida no descarte explícito; a limpeza automática de um temporário abandonado em encerramento abrupto antes da entrega ao sheet ficou pendente de um mecanismo seguro de propriedade.
