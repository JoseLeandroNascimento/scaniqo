# Plano de redesenho do preenchimento e da marcação sobre PDF

**Status:** interface implementada em 25/10/2026. A suíte unitária de `:app`, os novos testes de alvo de toque, a compilação `assembleDebug` e dois testes Compose no emulador passaram. As jornadas manuais com PDFs reais, TalkBack e reabertura do arquivo continuam como critérios de validação de produto.

**Decisão principal:** a entrada terá apenas **Texto** e **Marcação** como ferramentas. **Check e X serão tipos internos de Marcação**, nunca ferramentas independentes na barra principal. O redesenho combina uma barra compacta, ícones com significado claro e estados distintos para adicionar um elemento e editar um elemento existente.

**Precedência:** este plano substitui as decisões de apresentação e interação de Preencher das seções 4–7 e 11 do [plano v3](plano-preencher-ajustar-corrigir-usabilidade-v3.md) quando houver conflito. Em especial, substitui a entrada Texto/Check/X, os cards altos de propriedades e a inserção contínua misturada com a edição da última marca. Ajustar, Corrigir, assinatura e os fluxos de salvamento mantêm seus contratos atuais.

## 1 Objetivo e resultado esperado

O usuário deve reconhecer o que pode fazer olhando para a barra, tocar na página e ajustar o resultado sem procurar comandos em um menu extenso. O PDF deve continuar sendo o centro da tela. Os controles devem explicar a ação com ícone, rótulo e estado selecionado, sem transformar cada propriedade em um grande cartão.

O caminho principal será:

- **Escrever:** Texto → tocar na página → digitar → ajustar tamanho, cor ou formato → Concluir.
- **Marcar:** Marcação → escolher Check ou X → tocar nos locais desejados → Parar de marcar.
- **Editar:** tocar no texto ou na marca existente → ajustar o elemento selecionado → tocar fora para encerrar a seleção, ou Concluir.

Metas de interação:

- Encontrar Texto e Marcação sem rolagem, overflow ou leitura de instruções longas.
- Ver Check e X imediatamente ao entrar em Marcação.
- Mudar tamanho, cor e formato a partir de controles identificados na seleção de texto.
- Entender se escolher X afetará a próxima inserção ou a marca selecionada.
- Duplicar ou excluir em até dois toques, com nomes e ícones explícitos.
- Abrir e recolher ajustes sem perder texto, posição, zoom ou seleção.

## 2 Problemas encontrados na interface atual

| Evidência no código e na prévia | Efeito na experiência | Mudança necessária |
|---|---|---|
| `FillMarkControls` oferece Texto, Check e X no mesmo nível | O usuário precisa entender três ferramentas para realizar duas intenções | Unificar Check/X dentro de Marcação |
| `FillMarkChoice` usa caracteres T, ✓ e ✕ como ícones | Peso, proporção e leitura dependem da fonte | Usar vetores semânticos consistentes |
| Selecionar um elemento substitui os botões de inserção | Adicionar outro elemento deixa de ser evidente | Manter acesso explícito a adicionar Texto e Marcação fora do modo de digitação |
| Cabeçalho, Editar texto, cards de 64 dp e painel fixo de 184 dp se empilham | O painel domina a tela e repete títulos e valores | Barra de propriedades compacta e detalhes abertos sob demanda |
| Mais usa um hambúrguer e reúne muitas ações textuais | Parece navegação do app e esconde ações do elemento | Usar reticências, menu curto e ícones em cada ação |
| A seleção mostra várias alças grandes afastadas com linhas pontilhadas | O controle chama mais atenção que o texto e encobre formulários pequenos | Contorno discreto, movimento pelo elemento e uma alça principal de tamanho |
| `changeMarkKindOrMode()` pode alterar o elemento ativo enquanto `insert()` também resolve seleção | Preparar o próximo X pode ser confundido com converter o check anterior | Separar explicitamente inserir e editar |
| A descrição acessível do palco é fixa para escrever | O comando anunciado não corresponde ao modo Marcação | Descrever a ação disponível e seu estado atual |

Os caminhos de implementação estão na seção 11. A prévia de texto selecionado confirma a repetição de Tamanho e 16 pt em card, título e controle, além da altura ocupada antes de chegar ao ajuste.

## 3 Estrutura da tela proposta

### Organização vertical

1. **Topbar:** Voltar, Preencher PDF e **Concluir**, por extenso.
2. **Faixa da página:** página anterior/atual/próxima e histórico Desfazer/Refazer. Em janela estreita, a seleção de página pode ser um único botão que abre a navegação existente.
3. **PDF:** maior região disponível, com seleção discreta e controle de zoom no local já usado pelo editor.
4. **Barra inferior:** duas ações de criação, Texto e Marcação; uma linha contextual quando há modo de inserção ou elemento selecionado; no máximo um painel de detalhes aberto.

Desfazer/Refazer ficam sempre no mesmo local, inclusive durante a digitação. Ações indisponíveis permanecem reconhecíveis e desabilitadas. Concluir fecha a ferramenta após resolver um rascunho válido; salvar o documento continua no fluxo existente, com nome e tags quando aplicável.

### Esquema de referência

Os nomes entre colchetes representam botões com ícone vetorial e rótulo; os caracteres Check/X abaixo representam conteúdo, não especificam como desenhar os ícones.

```text
Voltar          Preencher PDF             Concluir
Página 1 de 3                     Desfazer  Refazer

                     PDF
             conteúdo e seleção

[ Texto ]             [ Marcação ]        [ Ações ]
──────── contexto da ação ou do elemento ─────────
```

Ações aparece apenas quando houver ações aplicáveis. Seu espaço pode ser reservado para evitar deslocar as duas ferramentas ao selecionar um elemento. O ícone é de reticências, nunca hambúrguer.

### Proporções e acabamento

- Usar as cores semânticas, formas e tipografia de `ScaniqoTheme`.
- Uma superfície inferior, separada do canvas por borda ou elevação discreta. Evitar cartões dentro de cartões, bordas fortes em todos os botões e sombras concorrentes.
- Referências iniciais: margens de 16 dp, intervalos de 8 dp, ícones de 20–24 dp e controles com alvo de toque mínimo de 48 dp.
- Barra de criação com altura visual de referência de 56–64 dp; linha contextual de 48–64 dp. São referências para fonte padrão, não alturas máximas impostas a textos ampliados.
- Sem detalhes abertos, buscar uma barra inferior de aproximadamente 112–144 dp quando existir contexto. Em modo neutro, mostrar apenas a linha de criação.
- Em um telefone de referência de 360 × 800 dp, com fonte padrão, teclado fechado e detalhes recolhidos, reservar ao canvas pelo menos 60% da área interna abaixo da topbar.
- Usar cor de destaque no modo ativo e na propriedade selecionada. Excluir usa lixeira; o vermelho fica restrito à ação destrutiva.
- Não usar gradientes, ilustrações ou animações contínuas atrás do PDF.

## 4 Texto e Marcação no primeiro nível

### Duas ações de criação

**Texto** usa um ícone de campo de texto com cursor. **Marcação** usa um ícone de caixa marcada. Os rótulos permanecem visíveis. O ícone principal de Marcação não vira um X quando o subtipo muda; o subtipo é exibido no contexto interno.

Os botões representam adicionar conteúdo. A semântica será “Adicionar texto” e “Adicionar marcação”. Um pequeno sinal de adição pode fazer parte do vetor, desde que continue legível em 24 dp.

Quando há um elemento selecionado, tocar em Texto ou Marcação resolve o rascunho válido, encerra a seleção e prepara a nova inserção. Isso não converte texto em marca nem converte a marca existente. A escolha de tipo dentro do contexto de uma marca selecionada é a ação que converte Check/X.

### Marcação aberta

Ao tocar em Marcação, exibir imediatamente um seletor interno agrupado:

```text
Adicionar marca
[ Check | X ]         [ Tamanho ]         [ Cor ]
Toque na página para marcar              [ Parar ]
```

- Check é o padrão na primeira entrada da sessão. Reentrar em Marcação preserva o último tipo usado na mesma sessão.
- Check e X têm amostra vetorial e rótulo. Apenas uma alternativa pode estar selecionada.
- A indicação de seleção usa preenchimento/borda; não acrescentar um check automático sobre o botão X selecionado.
- O grupo pertence visualmente ao contexto de Marcação, sem nova tela, aba principal ou ação global de X.
- Em largura padrão, o grupo ocupa a parte inicial da linha, e Tamanho/Cor usam atalhos compactos. Em fonte ampliada, mover Tamanho/Cor para a linha seguinte, sem esconder opções.
- A dica e Parar ficam na faixa contextual junto ao canvas; não adicionar uma terceira faixa permanente no dock apenas para a dica.

### Inserção contínua sem seleção ambígua

A ferramenta fica armada para marcar vários campos. Cada toque válido em uma área vazia insere exatamente uma marca e gera uma operação reversível. A marca recém-inserida recebe um breve realce, mas **não fica selecionada para edição** enquanto o modo de inserção contínua estiver ativo.

Nesse estado, trocar Check por X modifica apenas o tipo da próxima inserção. O check colocado anteriormente permanece intacto. Tamanho e cor atuais da sessão são preservados ao trocar o tipo. O modo de inserção nunca abre o teclado de texto.

Tocar em uma marca existente encerra a inserção contínua e seleciona aquela marca. Parar ou Voltar encerra somente o modo de inserção; as marcas já inseridas permanecem. Concluir encerra o modo e segue o contrato atual de saída da ferramenta.

## 5 Seleção e edição previsíveis

### Texto selecionado

Manter as ações de criação na primeira linha e mostrar uma linha contextual compacta:

```text
[ Editar texto ] [ Tamanho 16 pt ] [ Cor Azul ] [ Formato ]
```

Cada ação usa ícone e rótulo. O valor pode ocupar a segunda linha do mesmo botão, sem criar um card elevado. Remover a linha exclusiva “Texto selecionado” e a linha isolada de Editar texto da apresentação atual.

- Editar texto abre o teclado no elemento selecionado.
- Um segundo toque no mesmo texto também permite editar; esse gesto é um atalho, não a única maneira de encontrar a ação.
- Tamanho, Cor e Formato abrem apenas seu painel. O PDF mostra o efeito imediatamente.
- Ações do elemento oferece Duplicar e Excluir primeiro, seguidos dos ajustes menos frequentes.

### Marca selecionada

A faixa contextual informa **Editar marca** e apresenta Check/X, Tamanho e Cor no mesmo agrupamento usado para inserir. A legenda diferencia “Editar marca” de “Adicionar marca”.

Trocar Check/X nessa seleção modifica o mesmo `id`, preservando posição, dimensões, cor, opacidade e escopo. A contagem de elementos não muda. O seletor não prepara inserção contínua nesse estado.

O próximo elemento só é preparado ao tocar na ação principal Marcação. O feedback muda para Adicionar marca, a seleção é retirada e o primeiro toque na página efetivamente insere a nova marca.

### Ações do elemento

O botão de reticências com rótulo **Ações** abre um menu ancorado curto. Ordem:

1. **Duplicar**, com ícone de cópia. Cria uma cópia deslocada dentro da página, seleciona a cópia e permite movê-la; não abre o teclado automaticamente.
2. **Excluir**, com lixeira. Remove o elemento e oferece desfazer pelo histórico existente; não usar X como comando de excluir.
3. Separador e ações específicas: Posição precisa, Caixa de texto para texto, Repetir nas páginas quando houver mais de uma página.
4. Descartar alterações do elemento, com rótulo claro.

Lista de elementos e valores salvos ficam em um grupo secundário identificado. Não misturar remover um valor salvo com excluir o elemento do PDF. O menu deve rolar quando necessário; ações frequentes ficam no início.

### Toque fora e retorno

- Tocar fora de uma seleção resolve o rascunho válido e retira o contorno; esse toque não adiciona outro elemento.
- Em modo de inserção armado, tocar no vazio cria conteúdo. Os dois estados não coexistem.
- Um erro de entrada mantém o contexto e mostra o motivo junto ao controle; não consumir um toque de nova inserção sem feedback.
- Concluir mantém o nome textual. Check e X são exclusivamente tipos de marca nos controles desta ferramenta.

## 6 Propriedades compactas

### Tamanho

**Texto:** um único controle com diminuir, valor editável e aumentar. Preservar a faixa atual de 6–72 pt, o passo de 1 pt, números localizados e as regras de validação já existentes. Não repetir Tamanho e 16 pt em três níveis. O título do painel é suficiente; presets ficam recolhidos em “Tamanhos comuns” se mantidos.

**Marca:** mostrar Tamanho da marca, amostra e controles de diminuir/aumentar. Usar limites geométricos reais. Não apresentar pontos de fonte, campo de digitação de texto ou um slider de valores sem significado definido. Se forem adotados presets, sua correspondência geométrica precisa estar implementada e testada antes de aparecer na UI.

### Cor

Paleta única de cores do projeto, com amostra atual e nome. Usar até quatro colunas em telefone estreito, recentes sem duplicatas e borda contrastante em cores claras. Tocar em uma cor atualiza o elemento ou a configuração da próxima marca, conforme o contexto; não há Aplicar cor.

Opacidade fica numa seção secundária recolhida do mesmo painel. O ícone de Cor combina uma paleta com a amostra atual; o estado selecionado não depende apenas da cor.

### Formato

Mostrar três toggles compactos com ícone e rótulo: **Negrito**, **Itálico**, **Sublinhado**. Regular é o estado de Negrito desligado, evitando uma linha adicional Peso/Regular/Negrito que repete a mesma decisão.

A segunda linha reúne alinhamento Início/Centro/Fim com os vetores já existentes. Preservar direção do texto e semântica START/CENTER/END; não tratar Início como esquerda física para todos os idiomas.

Caixa de texto, quebra de linha, autoajuste e alinhamento vertical permanecem separados em Ações → Caixa de texto. Nenhuma família de fonte ou peso não suportado pelo PDF será exposta.

### Um painel de detalhes

- Cada painel tem título e ação **Recolher**, com chevron para baixo. Não usar X para fechar os ajustes ao lado do seletor de marca X.
- Tocar na propriedade ativa também recolhe o painel; Voltar faz o mesmo.
- Trocar de propriedade substitui o conteúdo no mesmo espaço.
- Definir a altura a partir da janela disponível e do controle principal. Remover a obrigação de 184 dp em todos os estados.
- Em altura reduzida, detalhes podem rolar com indicação visual de continuação. A propriedade ativa, Recolher e a ação principal continuam alcançáveis.
- Não empilhar painel, popup e modal. Abrir uma ação secundária suspende os detalhes e restaura o contexto ao fechar.

## 7 Teclado e área do documento

Durante a digitação, o dock completo vira uma faixa compacta acima do IME com Tamanho, Cor, Formato e Ocultar teclado. As ações de criar outro elemento retornam quando a digitação termina. O título Preencher PDF e Concluir permanecem visíveis.

Abrir uma propriedade encerra a composição de texto corretamente, preserva cursor/seleção e troca o teclado pelo painel. Fechar o painel não reabre o IME automaticamente; Editar texto solicita o teclado. O campo numérico de tamanho é um estado próprio, com teclado numérico e sem disparar edição de texto do PDF.

Ordem de Voltar: resolver/cancelar o buffer numérico conforme as regras atuais; fechar teclado; recolher painel; encerrar inserção ou seleção; depois seguir a política existente de saída com rascunho. Cada pressionamento atua sobre o estado visível mais próximo. Nenhuma mudança pode descartar texto ou aplicar um valor inválido silenciosamente.

O canvas deve preservar o ponto em edição quando o teclado ou o painel muda a área disponível. Não recentralizar a página em cada atualização, animar o viewport durante um arraste ou restaurar o zoom ao mudar cor.

## 8 Seleção diretamente na página

Substituir a aparência atual de várias alças afastadas e linhas cruzando o conteúdo por uma seleção de leitura simples:

- Contorno fino e contrastante ao redor da caixa, com distância visual curta do conteúdo.
- Arrastar o corpo do elemento move a seleção quando o teclado está fechado. Durante digitação, o toque interno pertence ao cursor e à seleção de texto.
- Uma alça principal de redimensionamento no canto inferior direito, com desenho pequeno e alvo de toque ampliado. Não desenhar todas as áreas de toque como círculos grandes.
- Para texto, a alça altera a caixa; o controle Tamanho altera a fonte. Preservar o comportamento de medição/autoajuste existente e explicar a função da alça na descrição acessível.
- Para marca, a alça redimensiona proporcionalmente, preservando a geometria de Check/X.
- Nas bordas, reposicionar apenas a apresentação da alça para a área alcançável; a transformação continua referenciada à âncora real do elemento. Usar um conector curto somente quando necessário.
- Elementos minúsculos têm foco e alvo de seleção ampliados, sem alterar sua geometria no PDF.
- Durante a digitação, recolher alças que disputem espaço com o cursor. Reexibi-las ao voltar à seleção.
- Posição precisa e comandos acessíveis de mover/redimensionar continuam disponíveis, inclusive quando o gesto não for adequado.

A troca de quatro alças pela apresentação simplificada deve ser uma entrega isolada. Preservar as funções de geometria que ainda forem usadas e testar cantos, zoom, coordenadas da página e sobreposição dos alvos antes de remover caminhos antigos.

## 9 Ícones e linguagem visual

| Ação | Ícone e asset previsto | Regra de apresentação |
|---|---|---|
| Texto | Novo `ic_fill_text.xml`, campo de texto/cursor | Rótulo Texto; sem `Text("T")` como ícone |
| Marcação | Novo `ic_fill_mark.xml`, caixa marcada | Rótulo Marcação constante; nunca X no primeiro nível |
| Check interno | `ic_check_24.xml` | Rótulo Check dentro do seletor de tipo |
| X interno | Novo `ic_fill_crossmark.xml` | Rótulo X; vetor próprio da marca, sem evento de fechar |
| Editar texto | `edit_24px.xml` ou vetor de cursor equivalente | Rótulo Editar texto, ação apenas para texto |
| Tamanho de texto | Novo `ic_fill_text_size.xml` | Valor em pt acompanhado de nome acessível |
| Tamanho de marca | `ic_gesture_resize.xml` | Sem confundir com redimensionar a página |
| Cor | `ic_settings_palette.xml` e amostra | Nome e valor selecionado anunciados |
| Formato | Novo `ic_fill_format.xml`, letra formatada | Rótulo Formato |
| Negrito, Itálico, Sublinhado | Novos vetores semânticos próprios | Estado selecionado e rótulos; não improvisar glyphs dependentes da fonte |
| Alinhamento | `ic_fill_text_start.xml`, `ic_fill_text_center.xml`, `ic_fill_text_end.xml` | Semântica de Início/Centro/Fim e suporte a RTL |
| Duplicar e Excluir | `ic_fill_duplicate.xml`, `delete_24px.xml` | Ações com texto no menu; lixeira para excluir |
| Ações | `more_vert_24px.xml` | Reticências, retirando `ic_app_menu` deste contexto |
| Recolher ajustes | Chevron para baixo, vetor existente adequado ou novo | Rótulo Recolher; não reutilizar X |
| Desfazer e Refazer | `ic_document_undo.xml`, `ic_document_redo.xml` | Local estável e descrições acessíveis |

Revisar os vetores lado a lado em 20–24 dp antes de aprovar: espessura, preenchimento, proporção e alinhamento óptico devem pertencer à mesma família. Os novos nomes são propostas de assets; conferir a pasta de drawables antes de duplicar um recurso equivalente. Não é necessário adicionar uma biblioteca de ícones nem atualizar dependências para este redesenho.

## 10 Estados e regras de implementação

Separar o estado da ferramenta de criação (`TEXT` ou `MARK`) do tipo persistido do elemento (`TEXT`, `CHECKMARK`, `CROSSMARK`). O modelo de PDF continua usando os tipos existentes.

| Estado da interface | Toque em área vazia | Escolher Check/X | Ações principais |
|---|---|---|---|
| Navegação sem ferramenta armada | Navega/desmarca, sem inserir | Seletor não aparece | Texto ou Marcação prepara inserção |
| Inserir texto | Cria rascunho e abre escrita | Não se aplica | Pode mudar para Marcação sem criar texto vazio |
| Inserir marca | Insere exatamente uma marca e mantém o modo armado | Configura a próxima inserção | Parar termina o modo; tocar em existente entra em edição |
| Texto selecionado | Resolve rascunho válido e retira seleção | Não se aplica | Editar, propriedades, Ações ou nova criação |
| Texto em digitação | Respeita foco/IME e termina escrita conforme gesto | Não se aplica | Propriedades trocam IME por painel |
| Marca selecionada | Resolve rascunho válido e retira seleção | Converte a marca selecionada sem alterar ID ou contagem | Propriedades, Ações ou nova criação |

Regras obrigatórias:

- `selectionId`, tipo da próxima marca e modo de inserção não podem ser inferidos do mesmo booleano.
- O estado Inserir marca não mantém uma seleção editável da última marca inserida.
- `withFillMarkKind()` continua sendo a primitiva de conversão de uma marca existente, preservando aparência e geometria.
- A inserção é uma operação atômica e reversível. Não duplicar `PutOverlay` entre tap, mudança de painel e Concluir.
- Cada inserção pode solicitar uma nova prévia. A renderização assíncrona não pode cancelar ou consumir silenciosamente os próximos toques: manter a superfície de interação estável, correlacionar o resultado à revisão atual e descartar prévias obsoletas. Uma base preservada com overlays transitórios é uma opção, desde que evite desenhar o mesmo elemento duas vezes. Se houver impedimento real de interação, torná-lo explícito antes do próximo toque.
- Classificar elementos por `kind` e capacidades, não por uma regra genérica “não é texto, então é marca”. Somente `CHECKMARK` e `CROSSMARK` entram no seletor Check/X. Preservar a edição textual já suportada para outros tipos sem convertê-los para `TEXT`; tipos não editáveis por Preencher ficam intactos, sem painel enganoso nem inserção acidental por cima ao tocar para selecioná-los.
- Histórico local atende ao rascunho; histórico do workspace atende a elementos já inseridos/aplicados. Desfazer uma marca inserida mantém o modo de inserção armado e remove apenas a última operação correspondente.
- Troca de página resolve rascunho válido, encerra inserção contínua e seleção e mantém o último subtipo da sessão para a próxima entrada explícita em Marcação.
- Na primeira entrada, carregar o estilo salvo existente; durante a sessão, alternar Check/X preserva tamanho/cor atuais. Converter uma marca selecionada nunca substitui seu estilo por uma preferência de outro tipo.
- Preferências guardam aparência; não guardar texto pessoal, ID, posição ou escopo de páginas.
- Preservar bloqueios `state.canEdit`, limites de 100 elementos por página e 2.000 caracteres, limites geométricos, entrada numérica localizada e tratamento de erros.
- Estado de seleção e rascunho continua restaurável na rotação. Ao voltar de segundo plano ou restaurar a tela, retomar edição com o conteúdo preservado, mas deixar inserção contínua desarmada para evitar marca acidental.
- Se o ID selecionado deixar de existir após desfazer/excluir, limpar a seleção e os callbacks associados.

## 11 Arquivos e componentes envolvidos

Os caminhos de código abaixo são relativos a `app/src/main/java/com/joseleandro/scaniqo/`.

| Arquivo ou grupo | Responsabilidade da mudança |
|---|---|
| `ui/documentEditor/components/FillMarkControls.kt` | Substituir cards/glyphs e decompor barra de criação, seletor de marca e atalhos de propriedades |
| Novos componentes pequenos no mesmo pacote | `FillMarkToolBar`, `FillMarkTypeSelector`, `FillMarkPropertyStrip`, `FillMarkPropertyHost` e menu contextual, se essa divisão reduzir acoplamento |
| `ui/documentEditor/components/DocumentFillMarkEditor.kt` | Modelar inserir/selecionar/digitar, distinguir conversão e criação, conectar menu, foco, histórico e conclusão |
| Novo estado de interação testável no editor | Centralizar transições e impedir combinações inválidas de seleção, inserção contínua e IME |
| `ui/documentEditor/components/DocumentFillMarkStage.kt` | Dicas e descrições por modo, prioridade de toque em existente, feedback de inserção e gestos |
| `ui/documentEditor/components/DocumentFillMarkSelection.kt` e geometria de seleção associada | Reduzir alças visuais e preservar alvos acessíveis, limites e âncoras |
| `ui/documentEditor/components/FillMarkTextAlignmentControls.kt` | Reaproveitar ícones e comportamento de alinhamento |
| `ui/documentEditor/components/FillMarkEditing.kt` | Preservar conversão Check/X, cópia, limites e primitivas de edição |
| `ui/documentEditor/components/FillMarkStylePreferences.kt` | Preservar preferências válidas sem misturar conteúdo do documento |
| `ui/documentEditor/components/DocumentEditorToolScreen.kt` | Integrar localização consistente de histórico e ação textual Concluir se necessário |
| `ui/documentEditor/components/DocumentInlineFillPreview.kt` | Ampliar cenários visuais do editor completo |
| `app/src/main/res/drawable/` | Vetores novos e reuso dos ícones semânticos |
| `app/src/main/res/values*/fill_mark_v4_strings.xml` | Novos rótulos/dicas em base e nas dez localidades verificadas pelo projeto |
| `app/src/test/` e `app/src/androidTest/` | Transições de estado, identidade das marcas, seleção e jornadas de UI |

Não migrar `PdfOverlayKind`, reescrever o compositor PDF ou alterar o bottom sheet de nome/tags para implementar uma organização visual. Qualquer ajuste de geometria deve ser separado da reorganização dos controles.

**Componentes compartilhados:** `DocumentOverlayEditor` também encaminha `DocumentTool.WATERMARK` para `DocumentFillMarkEditor`. Antes de alterar esse componente, conferir os chamadores e separar a configuração específica de `FORMS` da configuração das outras ferramentas. Este plano redesenha Preencher; preservar o comportamento atual de Marca d’água e acrescentar verificação de regressão desse caminho, sem alterar seus tipos, escopo ou resultado exportado como efeito colateral.

## 12 Animações funcionais

| Interação | Movimento proposto | Restrição |
|---|---|---|
| Armar Texto/Marcação | Cor e indicador em 120–160 ms | Estado funcional muda imediatamente |
| Escolher Check/X | Indicador de seleção curto | Desenho do PDF muda uma vez, preservando caixa e ID |
| Abrir/recolher detalhes | Fade com deslocamento de até 6 dp, 160–200 ms | Reservar o espaço final antes de interagir; não animar o documento continuamente |
| Trocar Tamanho/Cor/Formato | Crossfade curto no mesmo host | Não empilhar painéis nem deslocar a página a cada frame |
| Inserir marca | Realce/fade breve, escala visual opcional 0,96→1 | Hit test e posição final existem desde o primeiro frame |
| Selecionar elemento | Contorno e alça surgem em cerca de 120–140 ms | Texto não pulsa; arraste segue o dedo sem atraso |
| Desfazer/refazer | Atualização imediata e feedback localizado | Não bloquear novas ações por animação |

Respeitar animações desativadas/movimento reduzido. Não renderizar PDF ou carregar bitmap a cada frame. Distinguir efeito decorativo de andamento real; não mostrar processamento artificial ao trocar cor ou tipo de marca.

## 13 Acessibilidade e adaptação

A área interativa mínima será 48 × 48 dp, mesmo quando o desenho do ícone ou da alça for menor. Evitar áreas expandidas sobrepostas em elementos vizinhos; a documentação oficial explica essa diferença entre tamanho visual e alvo de toque. [Android Developers — padrões de acessibilidade](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

- Texto e Marcação sempre têm rótulos. Botões compactos sem texto precisam de nome acessível e tooltip; não depender apenas do desenho.
- O seletor anuncia grupo, tipo e seleção: “Tipo de marca, X, selecionado”. A descrição do palco diferencia “Toque para inserir X” de “Marca X selecionada para edição”.
- Toggles de estilo expõem função e estado; ícone decorativo dentro de botão já rotulado não cria anúncio duplicado. Usar a semântica de seleção/ação do Compose. [Android Developers — semântica](https://developer.android.com/develop/ui/compose/accessibility/semantics).
- Em 320 dp com fonte 1,5× e 2×, reordenar em linhas/grade. Não diminuir fonte, cortar Marcação, truncar ações essenciais ou criar rolagem horizontal para descobri-las.
- Em paisagem estreita, manter canvas e controles lado a lado quando houver largura suficiente; em janela ampla, usar painel lateral com a mesma ordem e estado.
- Respeitar insets do sistema e IME. Verificar o editor completo dentro do Scaffold, além de previews isolados.
- Preservar foco e rolagem quando um painel muda. Em RTL, o desenho de X/Check não muda de significado e alinhamentos continuam lógicos.

## 14 Ordem de implementação

### Entrega A Definir o comportamento antes de redesenhar

Extrair/modelar as transições da seção 10. Cobrir inserir próximo X sem converter o check anterior, editar marca existente sem duplicar, sair de inserção e resolver rascunho válido. Integrar com o histórico e os eventos existentes.

**Saída verificável:** os caminhos inserir e editar não compartilham uma seleção ambígua; o domínio PDF não precisa de migração.

### Entrega B Construir a barra e os ícones

Criar a dupla Texto/Marcação, seletor interno Check/X e menu Ações com reticências. Substituir os glyphs por vetores e criar previews dos estados neutro, inserir marca, texto selecionado e marca selecionada. Conferir proporção dos ícones e rótulos em tema claro/escuro.

**Saída verificável:** X está ausente do primeiro nível; Check/X aparecem ao abrir Marcação; nova inserção continua acessível após selecionar um elemento.

### Entrega C Compactar propriedades e integrar o teclado

Reorganizar Editar texto/Tamanho/Cor/Formato, remover títulos e tamanhos duplicados, acrescentar Recolher e usar apenas um host de detalhes. Integrar a faixa de digitação, buffer numérico e retorno explícito ao teclado.

**Saída verificável:** a tarefa “nome azul e em negrito” é feita com o PDF visível; fechar ajustes não reabre o teclado nem perde o cursor.

### Entrega D Simplificar a seleção na página

Alterar a aparência e quantidade de alças em tarefa própria, mantendo coordenadas e ações acessíveis. Testar pequeno/grande, bordas e zoom antes de remover a apresentação antiga. Adicionar os efeitos curtos da seção 12 sem animar posições durante gestos.

**Saída verificável:** texto pequeno continua legível, há um caminho evidente para mover/redimensionar e todos os cantos permanecem alcançáveis.

### Entrega E Validar a experiência completa

Executar testes focados de `:app`, build completo do app e testes instrumentados quando houver dispositivo. Consultar consumidores reais antes de ampliar builds. Repetir as jornadas abaixo com PDFs sintéticos e comparar exportação/reabertura.

**Saída verificável:** registrar evidências por jornada, corrigir falhas encontradas e só então marcar os critérios de aceite. Build verde não substitui avaliação de compreensão dos controles.

## 15 Testes e jornadas de aceite

### Testes de estado e geometria

Ampliar `FillMarkEditingTest`, testes de geometria/seleção e a cobertura de estado para verificar:

- Check→X e X→Check preservam ID, contagem, posição, dimensões, cor e opacidade da marca selecionada.
- Em inserção contínua, alternar subtipo altera apenas futuras inserções.
- Selecionar uma marca existente interrompe inserção; tocar fora apenas desmarca.
- Desfazer remove uma inserção por vez sem produzir outra marca nem deixar seleção obsoleta.
- Trocar para Texto não converte uma marca; trocar para Marcação não perde texto válido nem cria texto vazio.
- Nova alça não altera a transformação da página e continua utilizável nos quatro cantos com zoom.
- IME e painel numérico preservam as regras atuais de confirmação, cancelamento e dados inválidos.
- O chamador `WATERMARK` dos componentes compartilhados mantém os eventos, o escopo e o resultado anteriores ao redesenho de `FORMS`.
- Documento com texto, Check/X, carimbo, cabeçalho e marca d’água mantém cada `kind` e suas capacidades; nenhum tipo não suportado é convertido implicitamente.
- Toques sucessivos durante uma renderização lenta criam uma marca por toque aceito; resultados antigos não apagam feedback, duplicam overlays nem sobrescrevem a prévia atual.

### Testes Compose e revisão visual

Ampliar `FillMarkControlsInteractionTest` e acrescentar testes do fluxo do editor, usando os eventos reais sempre que possível:

1. Entrada mostra Texto/Marcação e não expõe X como ferramenta principal.
2. Marcação revela Check/X; inserir check, escolher X e inserir outro preserva o primeiro check.
3. Selecionar o primeiro check e trocar para X mantém a mesma quantidade de elementos.
4. Selecionar texto → Editar → Tamanho → Cor → Formato → Concluir preserva o conteúdo.
5. Ações → Duplicar/Excluir têm ícones e rótulos e alteram apenas o elemento alvo.
6. Recolher, Voltar e Ocultar teclado atuam no estado correto, sem descartar edição válida.
7. Falha de prévia/bloqueio desabilita ações necessárias e permite recuperação sem inserção duplicada.
8. Marcar vários campos com renderização atrasada mantém interação previsível e a quantidade exata de marcas; atualização da prévia não perde gestos.
9. Selecionar elementos de tipos misturados mostra apenas ações compatíveis e preserva seu tipo original.

Previews obrigatórios: neutro; inserir Check; inserir X; marca selecionada; texto selecionado; texto em digitação; Tamanho/Cor/Formato abertos; entrada numérica inválida; 320 dp; fonte 1,5×/2×; paisagem; tema escuro; RTL. A inspeção deve incluir o Scaffold com barras do sistema e teclado simulado, além dos componentes isolados.

### Jornadas manuais

Usar PDFs sintéticos digital e escaneado, com campos pequenos e conteúdo perto das bordas. Repetir escrever, marcar, converter marca existente, duplicar, excluir/desfazer, trocar página, concluir, salvar e reabrir. Conferir identidade dos elementos e fidelidade de texto/marcas no PDF exportado.

Para avaliar entendimento, pedir “marque este campo com X”, “mude esta marca para check” e “deixe este nome azul e em negrito”, sem indicar botões. Registrar procura por ações, conversões acidentais e dificuldade para voltar ao documento. Corrigir a interface quando o caminho só funciona após explicação verbal.

## 16 Critérios de aceite

- [x] Primeiro nível contém Texto e Marcação; X não aparece como ferramenta independente.
- [x] Entrar em Marcação apresenta Check e X com ícone, rótulo e seleção inequívoca.
- [x] Tipo da próxima marca e tipo da marca selecionada têm efeitos distintos e previsíveis.
- [ ] Inserção contínua não perde toques durante atualização de prévia nem converte elementos de outros tipos.
- [x] Não há confirmação/cancelamento representados por Check/X nos controles de preenchimento.
- [x] Ícones são vetoriais, coerentes e semanticamente adequados; Ações usa reticências.
- [x] Tamanho/Cor/Formato e Editar texto são encontráveis sem menu genérico de estilo.
- [ ] O dock recolhido cumpre a meta de área do PDF na janela de referência.
- [x] Duplicar/Excluir são alcançáveis em até dois toques e têm rótulos claros.
- [x] Uma seleção não fica coberta por alças grandes ou conexões atravessando o conteúdo.
- [ ] Painel, teclado, histórico e rascunho preservam foco, dados e posição.
- [ ] Fonte ampliada, alvos de toque, TalkBack, RTL e movimento reduzido foram verificados.
- [ ] Prévia e PDF salvo/reaberto mantêm aparência e posição.
- [ ] Bottom sheet de nome/tags, limites atuais e o caminho compartilhado de Marca d’água continuam funcionando.
- [ ] Resultados de testes, builds e jornadas estão registrados; falhas encontradas foram resolvidas.

## 17 Orientação para a implementação por IA

> Implemente este plano no código atual do Scaniqo. Comece pelos estados de inserir/editar e pela barra Texto/Marcação. Check e X são subtipos de Marcação; não recrie três ferramentas principais. Use ícones vetoriais com rótulos e preserve os tipos persistidos de PdfOverlay.
>
> Resolva primeiro as tarefas de adicionar marca, converter marca existente e editar texto. Evite compensar uma hierarquia confusa com mais cards, texto explicativo ou animação. Simplifique a seleção na página em uma entrega isolada, preservando geometria e acessibilidade.
>
> Mantenha histórico, composição IME, salvamento e exportação consistentes. Valide o editor completo em tela pequena, não apenas o componente de controles. Registre evidências antes de declarar os critérios concluídos.
