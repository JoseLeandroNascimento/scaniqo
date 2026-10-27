# Plano de melhorias — Preencher, Ajustar e Corrigir

**Status:** implementação técnica entregue em 25/10/2026; testes unitários, build debug e quatro testes Compose em emulador passaram. Os critérios que exigem jornada completa com PDFs exportados/reabertos e avaliação assistiva permanecem abertos.

**Base:** diagnóstico do código em 24/10/2026, após a implementação do [plano do editor v2](plano-editor-ferramentas-usabilidade-v2.md). A seção 2 retrata o estado anterior a esta entrega; o registro de implementação e validação está na seção 19.

**Próxima revisão de Preencher:** o [plano de layout e marcação v4](plano-preenchimento-marcacao-layout-v4.md) redefine a barra como Texto/Marcação, com Check e X internos, e substitui as decisões conflitantes de apresentação desta ferramenta. É uma proposta para implementação; o registro da entrega v3 e o planejamento de Ajustar/Corrigir permanecem neste documento.

**Objetivo:** tornar os controles fáceis de reconhecer e alcançar, manter o PDF visível durante a edição e permitir experimentar ajustes com confiança. A melhoria deve ser perceptível ao preencher um campo ou corrigir uma página, sem depender de ler explicações longas.

**Precedência:** este plano substitui as decisões de apresentação e interação de Preencher, Ajustar e Corrigir do v2 quando houver conflito, principalmente a confirmação de elemento por ✓, o sheet genérico de estilo e a divisão Tamanho/Margens. Preserva a base de renderização, geometria, receitas, edição de assinaturas e salvamento já implementada.

## 1. Resultado esperado

| Experiência atual | Experiência desejada |
|---|---|
| Vários botões, duas linhas, menus e um sheet extenso para mudar aparência | Selecionar o texto → tocar Tamanho, Cor ou Formato → ver a mudança no PDF |
| A−/A+, presets e campo com outro Aplicar aparecem juntos | Um único controle de tamanho com valor editável e passos precisos |
| ✓ e ✕ aparecem como marcas e como confirmar/cancelar edição | ✓ e ✕ representam marcas; concluir a ferramenta tem rótulo textual |
| Abrir e fechar estilo pode esconder/reabrir o teclado | O usuário alterna explicitamente entre escrever e ajustar |
| Ajustar divide tamanho e margens entre seções relacionadas | Um painel de Tamanho reúne enquadramento, margens e escala |
| Controles de Ajustar/Corrigir podem desaparecer atrás de “Ajustes” | A operação selecionada sempre apresenta seu controle principal |
| Tentar um ajuste depende de restaurar tudo ou voltar | Desfazer/refazer local e comparação Antes/Depois |
| Recorte usa pinça para mudar seleção; outros contextos usam pinça para zoom | Dois dedos navegam; um dedo e alças editam o conteúdo indicado |
| Processamento e resultado disputam espaço com controles | Prévia preservada, feedback localizado e estados explícitos |

### Metas de interação

- Cor, tamanho e negrito de texto selecionado: abrir a categoria em um toque; escolher uma cor, preset ou negrito em mais um toque. Digitação de tamanho exato tem seu próprio fluxo.
- Trocar uma marca de ✓ para ✕: um toque, preservando o mesmo elemento.
- Começar a escrever em texto já existente: selecionar → Editar texto, ou segundo toque no texto.
- Encontrar o controle principal de Ajustar/Corrigir sem abrir overflow ou botão genérico.
- Desfazer a última tentativa e comparar o resultado em um toque cada.
- Nenhuma ação frequente depender de rolagem horizontal descoberta por tentativa.
- Salvar continua sendo uma etapa distinta de aplicar uma ferramenta.

## 2. Diagnóstico do código atual

Os caminhos desta seção são relativos a `app/src/main/java/com/joseleandro/scaniqo/`.

| Componente | Evidência | Implicação para o redesenho |
|---|---|---|
| `ui/documentEditor/components/FillMarkControls.kt` | Barra ativa reúne tamanho, cor, duplicar/excluir, cancelar, Estilo, confirmar e overflow. Há linhas com rolagem horizontal. | Reduzir ações simultâneas e dar rótulos às propriedades frequentes. |
| `FillMarkProperties`, no mesmo arquivo | Sheet de até 520 dp mistura estilo, posição e todas as páginas. | Separar aparência, posicionamento e destino da ação. |
| `FillMarkStyle` / `FillMarkManualSize` | Tamanho se repete em stepper, presets e campo com botão Aplicar. Paleta também se repete. | Criar uma única origem visual para cada propriedade. |
| `DocumentFillMarkEditor.kt` | Propriedades/paleta ocultam o IME; fechar pode solicitar foco novamente. Confirmação, retorno à página e conclusão têm caminhos distintos. | Definir estados explícitos de escrita, seleção e painel; simplificar conclusão. |
| `DocumentFillMarkStage.kt` | Paginação é restringida enquanto existe edição pendente. | Resolver o rascunho válido ao navegar; explicar qualquer impedimento real. |
| `DocumentToolScreenLayout.kt` | Cabeçalho, modos de pelo menos 88×64 dp, dica, canvas, navegação, controles com teto de 28% e ações. Em pouca altura, controles ficam ocultos. | Trocar faixas concorrentes por área contextual estável. Não usar porcentagem fixa como solução universal. |
| `DocumentLayoutSheetContent.kt` | Posição, Tamanho, Margens e Girar. Escolher margem pode mudar FILL para FIT. | Reunir decisões de enquadramento e representar combinações de forma explícita. |
| `DocumentLayoutHandles.kt` | A alça de mover também pode centralizar por toque. | Tocar em uma alça não deve alterar a posição inesperadamente. |
| `DocumentCorrectionsSheetContent.kt` | Recortar/Fundo alternam edição e resultado. Fundo pode iniciar detecção automaticamente; conversão de texto pode ficar em opções secundárias. | Separar intenção, análise e revisão; expor consequências relevantes no contexto. |
| `DocumentManualCropEditor.kt` | Pinça transforma a seleção do recorte. | Padronizar navegação por dois dedos em tarefa técnica isolada. |
| `DocumentEditorToolScreen.kt` | Undo/Redo para preenchimento dependem de não haver rascunho; Ajustar/Corrigir não expõem histórico local. | Implementar a semântica de desfazer antes de acrescentar novos ícones. |

A base funcional já existe: seleção direta, marcas vetoriais, estilo persistido, tipografia compartilhada, geometria de posicionamento, prévias e receitas. A proposta reorganiza essa base; não pressupõe refazer o compositor PDF.

## 3. Escopo e limites

**Incluído nesta revisão:**

- Controles contextuais de texto e marcas; edição inline e alternância com teclado.
- Posição precisa de elementos como ação secundária separada do estilo.
- Layout, enquadramento, escala, posição e rotação da ferramenta Ajustar.
- Recorte, fundo, inclinação e curvatura de Corrigir.
- Histórico local, comparação, estados de carregamento/erro e animações funcionais.
- Adaptação a pouca altura, fontes grandes, TalkBack e movimento reduzido.

**Preservar:**

- Os tipos, estilos, IDs, limites e defaults atuais de `PdfOverlay`.
- Geometria e distinção entre mover conteúdo, mover elemento e navegar na prévia.
- Correções em uma página por vez: Corrigir mantém a página âncora.
- Escopo de páginas já existente em Ajustar.
- Contratos de aplicação ao workspace, exportação e salvamento. Alterar a interação de rascunho, cancelamento e conclusão somente conforme as seções 7 e 10.
- Bottom sheet de nome/tags para documentos novos gerados, quando o fluxo já o utiliza.

**Fora desta entrega:** novos motores de PDF, família tipográfica, pesos de fonte 100–900, OCR novo, remoção automática de objetos, correção automática de livros, alterações no catálogo/mesclagem/compressão e redesenho completo da assinatura. Regular/Negrito usa o modelo existente; não exibir capacidades sem suporte na exportação.

## 4. Direção visual e estrutura comum

### 4.1 Hierarquia

1. **Topbar simples:** voltar, título da ferramenta e uma ação final textual.
   - Preencher: **Concluir**.
   - Ajustar/Corrigir: **Aplicar**.
   - Não repetir essa ação em outro rodapé.
2. **Faixa discreta de contexto:** página ou escopo; desfazer/refazer; Comparar quando pertinente.
3. **Documento:** maior área útil, fundo neutro, sombra/borda discretas, alças com contraste.
4. **Dock inferior:** barra contextual fixa com rótulos e um único painel de propriedades.
5. **Painel lateral em janela larga:** mesmos controles, ordem e estado do dock inferior.

A faixa de contexto pode quebrar em duas linhas em fontes grandes. Não comprimir alvos ou truncar Aplicar/Concluir para manter uma linha artificial.

### 4.2 Acabamento

- Reutilizar `ScaniqoTheme`, superfícies e cores semânticas do Material já adotadas pelo projeto.
- Espaçamento em múltiplos de 4/8 dp, margens laterais de referência de 16 dp e superfícies com cantos coerentes com o tema.
- Ícones de 20–24 dp dentro de alvos de pelo menos 48 dp.
- Cor de destaque reservada à seleção e ação principal; evitar contorno, preenchimento e sombra em todos os controles.
- Valor atual visível junto ao nome: **Tamanho · 16 pt**, **Cor · Azul**, **Inclinação · +1,2°**.
- Usar pequenas amostras da página para explicar enquadramento e curvatura. Não adicionar banners ou mascotes que reduzam a área de edição.
- Dicas curtas junto ao estado que exige ajuda; não manter parágrafos instrutivos entre topbar e página.
- A interface de controles respeita fontScale; o tamanho do texto no documento continua representando pontos do PDF.

### 4.3 Área contextual estável

- Tamanho, Cor, Formato e demais propriedades são estados mutuamente exclusivos de um único host.
- Não empilhar bottom sheets. Se uma ação secundária exigir modal, suspender o painel anterior e restaurá-lo ao fechar.
- Trocar o conteúdo dentro do painel sem redimensionar continuamente a página.
- Reservar uma área compacta para os controles comuns. Expandir detalhes somente por ação explícita.
- Em tela baixa, manter visíveis a categoria ativa, o ajuste principal e a ação final. Detalhes podem rolar/expandir com affordance clara.
- Não esconder automaticamente todos os controles por estar abaixo de 560 dp.
- Durante gesto ativo, congelar as dimensões do viewport. Expansões ficam para depois do gesto.
- Preservar a âncora visual do elemento quando IME/painel mudar o espaço disponível; não recentralizar a página a cada atualização.

## 5. Preencher: controles por intenção

### 5.1 Sem elemento selecionado

Mostrar três opções com ícone e nome:

```text
[ Texto ]          [ ✓ Check ]          [ ✕ X ]
Toque na página para adicionar
```

- Uma opção selecionada por vez; estado e nome anunciados ao TalkBack.
- Texto abre edição inline após tocar na página.
- Check/X permitem inserção consecutiva; enquanto esse modo estiver ativo, a dica diz **Toque para adicionar outra marca**.
- Selecionar um elemento existente interrompe a inserção contínua.
- Valores salvos e lista de elementos continuam disponíveis em **Mais**, com rótulos claros.
- Um modo de foco pode recolher detalhes, mas deve deixar uma ação textual reconhecível para reabrir os controles.

### 5.2 Texto selecionado

```text
Texto selecionado       [ Editar texto ] [ Mais ]
[ Tamanho · 16 pt ]   [ Cor ● ]   [ Formato ]
──────── painel da propriedade selecionada ───────
```

- Editar texto tem nome; não depende de reconhecer um lápis.
- Tamanho, Cor e Formato permanecem acessíveis enquanto o painel muda.
- Duplicar, Excluir, Posição precisa, Caixa de texto e Repetir nas páginas vão para Mais, agrupados por intenção.
- Ações de aparência não são misturadas com aplicação em todas as páginas.
- Formato pode mostrar um indicador discreto do estilo ativo, sem acumular siglas na barra.
- Em 320 dp, as três propriedades devem caber com rótulos. Quando fontScale exigir, usar grade ou quebra planejada; nunca depender de rolagem horizontal para a terceira ação.

### 5.3 Marca selecionada

```text
Marca selecionada                       [ Mais ]
[ ✓ Check | ✕ X ]     [ Tamanho ]     [ Cor ● ]
```

- A troca Check/X altera o mesmo ID e preserva caixa, cor, opacidade e posição.
- Não mostrar fonte, alinhamento de texto, B/I/U ou editar texto.
- A mudança do tipo do elemento selecionado não deve inserir uma segunda marca.
- Ao terminar essa edição, o modo de inserção deve ficar explícito; não criar uma marca no mesmo toque usado para desmarcar.

## 6. Painéis de estilo

### 6.1 Tamanho

**Texto:**

- Uma única linha principal: **−  16 pt  +**. Valor central tocável abre edição numérica no mesmo lugar.
- Manter faixa atual de 6–72 pt. Os passos dos botões são de 1 pt.
- Presets 12, 16 e 24 pt podem aparecer como atalhos secundários no mesmo painel, sem outro stepper ou campo permanente.
- Não manter o formulário adicional “Tamanho manual + Aplicar”.
- Done do IME ou Enter confirma o valor uma vez. Back/Escape cancela somente o buffer numérico e recupera o último valor efetivo.
- Concluir com buffer válido confirma o número e depois resolve o elemento, sem etapa extra. Com buffer inválido, mantém o campo aberto e explica o erro.
- Trocar de categoria ou fechar o campo descarta o buffer ainda não confirmado; selecionar um preset descarta esse buffer e aplica o preset escolhido. Essas regras devem ser compartilhadas pelos controles numéricos das demais ferramentas, usando Aplicar como ação final quando pertinente.
- Valor inválido mostra motivo local e não altera a caixa. Não aplicar clamp silencioso de “99” para “72”.
- Não arredondar um valor legado apenas porque o painel foi aberto; preservar valor real até uma alteração explícita.

**Marca:**

- Rotular **Tamanho da marca**, com miniatura da marca e controles −/+.
- Reutilizar os limites geométricos atuais; não reaproveitar o intervalo de tamanho de fonte.
- A alça no documento continua sendo o caminho de redimensionamento direto.
- Se forem adicionados Pequena/Média/Grande, definir e testar a correspondência em unidades da página antes de expor os presets.

### 6.2 Cor

- Grade de oito cores existentes, em até quatro colunas em telefone estreito.
- Mostrar as recentes sem duplicatas; quebrar em linhas quando necessário.
- Exibir o nome da cor selecionada e seleção por borda/indicador, além da cor.
- Branco/amarelo precisam de indicador contrastante sobre fundos claros.
- Seleção atualiza o documento imediatamente e mantém o painel aberto; não exigir Aplicar cor.
- Não repetir a mesma paleta em menu flutuante e sheet.
- **Opacidade** fica em seção recolhida, identificada, dentro de Cor. Reutilizar valores e capacidade atuais; não ocupar a primeira linha de preenchimento.
- Preservar estilo por tipo e cores recentes; não armazenar texto pessoal nessas preferências.

### 6.3 Formato

Ordem da informação:

1. **Peso:** Regular / Negrito, ambos escritos por extenso.
2. **Itálico** e **Sublinhado:** toggles com amostra visual e rótulo.
3. **Alinhamento horizontal:** Início, Centro e Fim, correspondendo a START/CENTER/END do modelo. Ícones e nomes acessíveis acompanham a direção do texto; não converter para esquerda/direita físicas apenas na interface.

Separar **Caixa de texto** das propriedades de letras:

- Autoajuste, quebra e alinhamento vertical ficam nessa ação secundária.
- Mostrar miniatura simples e explicação curta do comportamento real.
- Evitar expor alternâncias que parecem mudar a fonte mas redimensionam a caixa.
- Não inserir seletor de família, tracking, pesos variáveis ou outras opções sem suporte comum na medição, prévia e PDF exportado.

### 6.4 Posição, repetição e exclusão

- **Posição precisa:** setas nomeadas, centralizar e valor/feedback compreensível; preservar limites reais da página. Sem coordenadas normalizadas expostas ao usuário.
- **Repetir nas páginas:** ação separada, com escopo explícito e quantidade antes da confirmação. Reutilizar o contrato atual; não acionar repetição ao fechar um painel de cor.
- **Excluir:** ação identificada no menu; feedback com Desfazer, sem diálogo de confirmação a cada elemento.
- **Duplicar:** cria outro ID, seleciona a cópia e mantém estilo; limitar/desabilitar com motivo quando o limite real for atingido.

## 7. Escrita, conclusão e histórico do preenchimento

### 7.1 Uma conclusão de ferramenta

A versão final desta proposta **remove o ✓/✕ de confirmar/cancelar elemento**. Esses símbolos ficam reservados às marcas dentro do fluxo de preenchimento.

- Ajustes atualizam o rascunho visual.
- Tocar no vazio, selecionar outro elemento ou mudar de página confirma uma vez o rascunho válido antes de prosseguir.
- O toque usado para desmarcar não insere outro objeto.
- **Concluir** resolve o rascunho válido e retorna ao editor. Não exigir “Confirmar elemento” antes.
- Texto novo vazio pode ser descartado sem criar um objeto.
- Texto existente tornado vazio não é excluído implicitamente: mostrar **Digite um texto ou use Excluir**, mantendo a possibilidade de reverter.
- **Reverter edição do elemento** em Mais recupera o snapshot anterior à seleção; elemento novo é descartado.
- Aplicar/Concluir não grava silenciosamente outro arquivo nem abre nome/tags em toda mudança de estilo.

Esta decisão exige alteração de estado/eventos, não apenas remover os botões antigos.

### 7.2 Histórico sem perda de rascunho

Implementar um coordenador pequeno da sessão de edição, aproveitando o histórico do workspace:

- Enquanto há rascunho selecionado, desfazer/refazer opera primeiro sobre alterações locais.
- Um arraste, uma interação contínua de tamanho e uma escolha de propriedade são operações completas; não registrar frames.
- O coordenador da sessão é a única fonte do histórico acionado pelos botões e atalhos do app. Uma sessão contínua de digitação forma um grupo, consolidado ao abrir painel, trocar elemento ou concluir.
- Desfazer durante digitação finaliza a composição atual quando necessário, sem perder caracteres, e desfaz esse grupo. Mantém o elemento selecionado e restaura a seleção de texto válida do snapshot. O próximo texto digitado inicia outro grupo.
- Atalhos de desfazer/refazer tratados pelo app passam pelo mesmo coordenador; consumir o evento uma única vez, sem executar também undo nativo independente.
- Ao confirmar o elemento, consolidar a edição em um comando do workspace; não aplicar duas vezes em callbacks de foco, Back e Concluir.
- Quando não houver alterações locais para desfazer, encerrar a seleção sem novo PutOverlay e despachar um único Undo do workspace. Descartar pilhas locais antigas; qualquer nova seleção parte do estado resultante. Não manter refazer local de um elemento removido/substituído pelo Undo global.
- Nova alteração invalida o refazer correspondente. Abrir painel, comparar ou navegar sem alteração não cria histórico.
- Não persistir bitmaps no histórico. Preservar os limites já adotados.
- Testar o limite entre rascunho e workspace antes de liberar essa semântica para o usuário.

### 7.3 Teclado e Back

| Evento | Comportamento esperado |
|---|---|
| Primeiro toque em texto | Seleciona sem abrir teclado |
| Editar texto ou segundo toque | Abre IME no texto selecionado, preservando cursor quando pertinente |
| Tocar Cor/Tamanho/Formato durante escrita | Guarda texto/seleção, finaliza composição quando necessário, fecha IME e apresenta aquele painel |
| Fechar painel | Recolhe detalhes; não reabre o teclado sozinho |
| Voltar ao teclado | Ação explícita que recupera foco e seleção |
| Back/Escape com buffer numérico aberto | Cancela o buffer conforme seção 6.1; tem precedência sobre as regras gerais abaixo |
| Back com IME aberto | Fecha IME; mantém edição e elemento |
| Back com painel expandido | Recolhe painel; mantém rascunho |
| Back na ferramenta | Usa política de saída existente para alterações pendentes; nada é descartado silenciosamente |
| Concluir | Resolve primeiro o buffer numérico, se houver; depois finaliza uma vez o rascunho válido e a ferramenta |

Acima do IME, mostrar apenas a faixa compacta de propriedades e ação de voltar à escrita quando aplicável. Não colocar o sheet de 520 dp inteiro sobre o teclado. Preservar texto e seleção do `TextFieldValue`; estilizar não pode substituir o conteúdo por uma cópia antiga. A composição pode ser finalizada ao fechar/recriar a conexão do IME, sem perda de caracteres. Não prometer manter uma composição ativa entre conexões de teclados diferentes.

## 8. Ajustar: três categorias compreensíveis

Agrupar as quatro categorias atuais em **Posição**, **Tamanho** e **Girar**. Margens passa a fazer parte de Tamanho.

```text
← Ajustar                             Aplicar
[ Esta página ▾ ]      Desfazer Refazer Comparar
┌───────────────────────────────────────────┐
│                 DOCUMENTO                 │
└───────────────────────────────────────────┘
[ Posição ]        [ Tamanho ]        [ Girar ]
          controles da categoria ativa
```

O desenho é hierárquico, não uma exigência de caber todas as palavras da faixa de contexto em uma linha de 320 dp.

### 8.1 Posição

- Arrastar o conteúdo diretamente na folha; manter nove âncoras da geometria atual.
- Grade 3×3 intitulada **Alinhar**, com miniaturas que mostram topo, centro, base e cantos.
- Nome acessível específico: **Alinhar ao canto superior esquerdo**, não “posição 1”.
- Tocar na alça apenas seleciona/prepara o arraste. Centralizar é ação explícita.
- Guias aparecem durante movimento e desaparecem ao terminar; não deixar uma grade pesada sobre todo o PDF.
- **Posição precisa** fornece alternativa ao arraste; não precisa disputar espaço com a grade em todos os momentos.
- Quando um eixo não tiver espaço de deslocamento, explicar somente esse eixo e oferecer **Reduzir tamanho**.
- Não prometer deslocamento quando o conteúdo já ocupa toda a largura/altura. Não criar margens invisíveis para simular movimento.
- Preservar o posicionamento real no topo/esquerda/cantos corrigido no v2.

### 8.2 Tamanho e enquadramento

Três opções com miniaturas demonstrativas:

| Opção | Correspondência no modelo | Informação ao usuário |
|---|---|---|
| Com margem | FIT + edgeToEdge=false + contentScale=1 | Conteúdo inteiro dentro da área com margem |
| Sem margem | FIT + edgeToEdge=true + contentScale=1 | Conteúdo inteiro usando a área disponível |
| Preencher | FILL + edgeToEdge=true + contentScale=1 | Pode cortar bordas; a prévia mostra a área fora da folha |

- Sem margem não significa preencher ambos os eixos: a proporção pode deixar espaço livre.
- Preencher não pode ser apresentado como equivalente a remover margem.
- Escolher um preset aplica a combinação acima e escala de 100% como uma única operação desfazível. Informar junto às opções: **Enquadramentos usam escala de 100%**; o controle de escala acompanha o valor real imediatamente.
- Preservar posição normalizada/alinhamento e rotação ao escolher preset. Recalcular apenas a posição física resultante pela geometria atual, sem centralização adicional.
- Abrir o painel não normaliza nem altera receita existente. Alterar escala depois do preset pode recortar conteúdo; a prévia deve mostrar isso.
- Mostrar estado Personalizado quando a combinação, inclusive escala, não corresponder a um preset. Manter visíveis o modo de encaixe e o percentual reais, sem selecionar uma miniatura incorreta.
- Abaixo, uma linha de **Escala · 100%**, slider e −/+; valor tocável para edição precisa.
- Preservar intervalo atual de 25–200%; botões em passos de 1 ponto percentual.
- Não oferecer margens em mm enquanto o modelo só representa margem padrão/ausência de margem.
- Restaurar tamanho afeta escala/enquadramento/margem desta seção, mantendo posição e rotação, salvo ajuste geométrico indispensável comunicado pela prévia.

### 8.3 Girar

- Botões amplos **90° à esquerda** e **90° à direita**, com miniatura de orientação.
- Valor/orientação atual visível; restaurar somente a rotação.
- Girar trabalha em quartos de volta. **Endireitar**, em Corrigir, trabalha com inclinação fina.
- Gesto de giro existente pode permanecer como atalho se não conflitar com a navegação; o caminho principal é visível e acessível.

### 8.4 Escopo

- Reutilizar seleção de páginas existente de Ajustar.
- Contexto mostra **Esta página** ou **4 páginas selecionadas** antes de Aplicar.
- Trocar página de inspeção não altera o alvo nem descarta o rascunho.
- Aplicar usa exatamente os IDs exibidos; clique repetido não repete a operação.
- Não copiar esse seletor para Corrigir nesta entrega.

## 9. Corrigir: editar, conferir e aplicar

Quatro operações: **Recortar**, **Fundo**, **Endireitar** e **Curvatura**. Uma categoria ativa de cada vez, sem linhas permanentes adicionais de navegação.

Em pouca largura/fontes grandes, usar grade 2×2 para as categorias em vez de cortar rótulos ou esconder a última por rolagem.

### 9.1 Recortar

- Entrar com a área atual e alças visíveis.
- Preservar quatro cantos, quatro bordas, movimento interno e lupa já implementados.
- Um dedo na alça ajusta o recorte; arraste dentro da seleção move a seleção; dois dedos navegam pela visualização.
- Área descartada recebe máscara discreta; manter a leitura dos limites.
- Ação **Ver resultado** junto aos controles muda a apresentação para o resultado; a ação de retorno vira **Editar recorte**.
- Evitar duas linhas de tabs “Manual/Resultado” e “Antes/Depois” simultâneas.
- **Restaurar recorte** afeta somente esse ajuste e pode ser desfeito.
- Não acrescentar botão de recorte automático ou presets de proporção sem implementar e testar a capacidade correspondente.

### 9.2 Fundo

Nesta revisão, substituir o início automático por uma intenção explícita:

1. Ao abrir Fundo, mostrar o estado atual e a ação **Detectar contorno**; oferecer **Ajustar manualmente**.
2. Ao iniciar, feedback **Detectando contorno…**, animação restrita à prévia e **Cancelar**.
3. Resultado apresenta a região mantida e as ações **Ajustar contorno** / **Detectar novamente**.
4. Em erro ou ausência de contorno, oferecer ajuste manual imediatamente, preservando o último estado válido.
5. Nunca sobrescrever ajuste manual por resposta atrasada ou nova análise iniciada só por trocar de aba.

O rótulo explica o mecanismo existente de contorno/máscara. Não prometer remoção inteligente de sombras ou objetos se o processamento atual não oferece isso.

### 9.3 Endireitar

- Régua/slider horizontal com zero evidente e guia horizontal durante a interação.
- Valor tocável com sinal e unidade: **−1,2°**.
- Botões −/+ em passos de 0,1°, mantendo intervalo de −15° a +15°.
- **Zerar inclinação** é ação local; preservar recorte, fundo e curvatura.
- Permitir ajuste direto já disponível, com a mesma função de conversão utilizada pelo painel.
- Entrada numérica respeita separador decimal do idioma; valor inválido não altera a prévia.

### 9.4 Curvatura

- Título complementar curto: **Corrigir página curvada**.
- Duas miniaturas mostram os sentidos do efeito; slider com zero central.
- Preservar faixa de −100% a +100%, correspondente a −1..1 no modelo.
- Valor editável e passos de 1 ponto percentual, com unidades consistentes.
- **Zerar curvatura** é independente dos outros ajustes.
- Não apresentar como correção automática de livros; usuário ajusta e compara o efeito real.

### 9.5 Conversão de texto para imagem

- Preservar as regras atuais de `rasterizeText`, `preservesNativeContent` e rasterização exigida por filtros.
- Quando a operação exigir decisão, mostrar informação visível junto ao ajuste e antes de Aplicar.
- Texto proposto, condicionado ao efeito real: **Esta correção precisa converter o conteúdo da página em imagem. O texto original pode deixar de ser selecionável.**
- Oferecer a escolha exigida pelo contrato atual; não consentir automaticamente nem esconder no overflow.
- Se um filtro anterior já exigir rasterização, explicar o estado fixado em uma linha e preservar a dependência.
- Não exibir aviso genérico em páginas escaneadas para as quais a condição não se aplica.
- Limpar uma correção não pode desligar rasterização exigida por outro efeito.

## 10. Comparação, histórico e gestos de Ajustar/Corrigir

### Comparar

- Uma ação **Comparar** alterna entre Antes e Depois, com o estado atual escrito sobre a prévia.
- **Antes** representa o documento ao entrar nesta sessão de ajuste, incluindo alterações anteriores; não o arquivo bruto original.
- Em Ajustar, guardar o baseline por ID de cada página do workspace na entrada da sessão. Alvos acrescentados ao escopo usam seu próprio estado inicial, não o layout da página âncora. Renderizar sob demanda e invalidar a sessão se a revisão externa do workspace mudar de forma incompatível.
- Mesma página, zoom e enquadramento nos dois estados. Nenhuma operação é aplicada ao comparar.
- Enquanto Antes estiver visível, a prévia é de inspeção. Acionar um controle de edição retorna a Depois antes de alterar o rascunho.
- Em Recortar/Fundo, Comparar aparece na apresentação de resultado; a edição de contorno mantém somente **Ver resultado**. Assim não há duas navegações competindo.
- Pressionar e segurar pode ser atalho futuro, nunca o único acesso; toque alternável atende TalkBack e limitações motoras.
- Se uma das imagens ainda não estiver pronta, informar preparação e impedir comparação com imagem de outra página/versão.

### Histórico local

- Ajustar/Corrigir usam snapshot inicial e pilhas locais do rascunho; não acionar Undo global do workspace para simular desfazer um slider ainda não aplicado.
- Uma entrada por gesto finalizado, preset, número confirmado, restauração ou resultado aceito da detecção de contorno.
- A detecção só cria entrada quando sessão, página, requisição e revisão ainda correspondem ao rascunho. Desfazer restaura a máscara anterior; cancelar/ignorar resposta não cria entrada.
- Trocar categoria, painel ou modo de comparação não cria histórico.
- **Restaurar [categoria]** recupera os campos daquela categoria no snapshot de entrada da sessão, é reversível e não remove outras alterações. Ações chamadas **Zerar inclinação/curvatura** atribuem explicitamente zero; não confundir zerar com recuperar o valor inicial.
- Uma ação secundária **Descartar todos os ajustes desta sessão** recupera o snapshot inicial; não confundir com apagar correções antigas já salvas.
- Aplicar entrega um comando coerente ao workspace conforme eventos existentes; manter o retorno/feedback da rota.
- Back preserva a política de rascunho pendente. Nova sessão só começa após conclusão/cancelamento efetivo.
- Restauração após rotação/mudança de janela preserva rascunho, escopo e categoria. Documentar a política de histórico após morte de processo; nunca anunciar continuidade que não foi implementada.

### Gestos

- Dois dedos: zoom/pan da visualização em todos os modos.
- Um dedo na alça/área ativa: edição da seleção correspondente.
- Segundo dedo cancela a transformação parcial antes de iniciar navegação; não aplicar um salto residual.
- Mudança da pinça de recorte é uma entrega isolada com testes de coordenadas, CropBox, rotação e zoom.
- Preservar distinção entre coordenadas da captura, página, conteúdo e tela.
- Alças têm área de toque expandida; isso não reduz o alcance real do conteúdo até a borda.
- Durante arraste, nenhuma animação reposiciona o canvas ou o objeto independentemente do dedo.

## 11. Animações que ajudam a entender

Os tempos são referências de design; usar as convenções e APIs já disponíveis no projeto. Ajustar por inspeção real, sem testes que apenas repitam números.

| Momento | Animação proposta | Restrição |
|---|---|---|
| Selecionar categoria/propriedade | Cor e indicador em 120–180 ms | Feedback imediato; não atrasar callback |
| Trocar painel contextual | Crossfade e deslocamento de 6–8 dp em 160–220 ms | Área externa estável; respeitar direção/RTL |
| Selecionar elemento | Entrada breve de contorno/alças | O conteúdo da página não pulsa |
| Inserir marca | Fade e escala discreta 0,96→1, uma vez | Caixa de hit test e posição já são finais |
| Trocar Check/X | Transição curta da amostra/controle | Sem criar duas marcas ou mudar dimensões |
| Mostrar guias de posição/inclinação | Fade curto ao iniciar/terminar gesto | Sem animação contínua de grade |
| Alcançar alinhamento | Realce breve e haptic opcional | Apenas ao entrar no ponto, sem disparo por frame |
| Atualizar prévia | Manter último resultado válido e indicador pequeno | Não piscar a página nem bloquear todo o canvas |
| Detectar contorno | Traço discreto sobre a prévia durante trabalho real | Cancelável; sem porcentagem inventada ou duração mínima |
| Comparar Antes/Depois | Troca curta ou instantânea | Evitar morph/crossfade longo que atrapalhe avaliar bordas |
| Aplicar com sucesso | Feedback tonal/check breve e texto de resultado | Só após sucesso real; erro mantém rascunho |

- Movimento reduzido/animações desativadas: estados mudam diretamente ou com fade mínimo permitido.
- Nenhum processo espera a animação terminar.
- Não renderizar PDF, fazer I/O ou carregar bitmaps a cada frame de animação.
- Preparar prévias com cancelamento/coalescência já existentes; resultado deve corresponder ao ID da página e à revisão do rascunho.
- Manter duas prévias de comparação somente com política de memória adequada; não guardar cópias por passo de histórico.

## 12. Estados e recuperação

| Situação | Resposta da interface |
|---|---|
| Fonte da página carregando | Estado localizado na prévia; controles dependentes indisponíveis com motivo |
| Fonte indisponível/não suportada | Mensagem curta, tentar novamente quando cabível, retorno seguro |
| Preview em atualização | Última prévia válida + indicador; resultado antigo não se apresenta como atual |
| Erro na prévia | Preservar rascunho e controles; tentar novamente. Não afirmar resultado validado |
| Análise cancelada | Restaurar disponibilidade e último ajuste válido |
| Resposta atrasada | Ignorar por sessão/página/revisão |
| Documento bloqueado | Reutilizar estado e fluxo existentes de desbloqueio |
| Aplicação em andamento | Bloquear duplo envio, mostrar estado real e preservar contexto |
| Falha ao aplicar | Permanecer na ferramenta com rascunho recuperável |
| Campo numérico inválido | Erro junto ao campo; preservar último valor efetivo |
| Nenhuma alteração | Aplicar indisponível com estado acessível; voltar continua possível |
| Efeito limitado pela geometria | Explicar limitação concreta e oferecer ajuste pertinente |
| Saída com alteração pendente | Fluxo atual de manter/descartar; nunca descarte silencioso |

Aplicar modifica o workspace. Persistência continua no fluxo do editor. Documento novo gerado utiliza o bottom sheet existente para nome/tags quando pertinente; não criar um segundo formulário de salvar dentro dos painéis.

## 13. Acessibilidade e layouts de validação

- 320, 360 e 390 dp; retrato, janela baixa e paisagem larga.
- Fontes 1×, 1,5× e 2×, sem corte de ações essenciais.
- Grade/quebra para ações de primeira necessidade; rolagem vertical evidente para detalhes.
- Painel lateral em janela larga preservando os mesmos estados, sem instância duplicada do rascunho.
- Temas claro e escuro; seleção diferenciada por forma/indicador e semântica, além de cor.
- TalkBack informa propriedade, valor, tipo, seleção, disponibilidade e motivo relevante.
- Sliders com ações de incremento/decremento e valores com unidade; alternativa numérica para precisão.
- Alvos de pelo menos 48 dp; tooltips complementam ícones, não substituem rótulos centrais.
- Ordem de foco: contexto → elemento/preview → categorias → controles → ações secundárias.
- Mudança de painel leva foco ao título/controle pertinente; fechamento retorna ao acionador.
- Teclado físico: Tab/Shift+Tab, Enter nos campos, Escape/Back coerente e atalhos sem duplicação de eventos.
- Não anunciar cada frame do slider; anunciar valor final ou de forma controlada.
- Conteúdo nos quatro cantos permanece editável com zoom e teclado.
- RTL não inverte semanticamente uma posição física da página por acidente; usar descrições consistentes com a geometria.
- Controles da interface respeitam tema, fonte e movimento; documento mantém fidelidade de exportação.

## 14. Mapa técnico proposto

Caminhos relativos a `app/src/main/java/com/joseleandro/scaniqo/`. Nomes de novos componentes são sugestões, não exigência de arquitetura.

| Área | Arquivos existentes | Trabalho |
|---|---|---|
| Host da ferramenta | `ui/documentEditor/DocumentEditorToolScreen.kt`, `DocumentEditorToolRoute.kt` | Posicionar ação final única e rotear histórico conforme sessão |
| Preenchimento | `ui/documentEditor/components/DocumentFillMarkEditor.kt`, `DocumentFillMarkStage.kt`, `FillMarkControls.kt` | Estados de seleção/escrita/painel, commit atômico e paginação |
| Estilo | `FillMarkTextAlignmentControls.kt`, `FillMarkStylePreferences.kt` | Separar aparência/caixa, reutilizar preferências |
| Base visual | `DocumentToolScreenLayout.kt` | Dock contextual estável e apresentação lateral |
| Ajustar | `DocumentLayoutSheetContent.kt`, `DocumentLayoutHandles.kt` | Três categorias, presets honestos, alça sem centralização acidental |
| Corrigir | `DocumentCorrectionsSheetContent.kt`, `DocumentManualCropEditor.kt`, `DocumentCorrectionHandles.kt` | Estados de editar/resultado, análise explícita, ajuste fino e gestos |
| Histórico/eventos | `ui/documentEditor/DocumentToolsViewModel.kt` e estado/eventos atuais | Integrar fronteira local/workspace sem aplicar duas vezes |
| Textos | `app/src/main/res/values*/` | Rótulos, valores, erros e semântica em todos os idiomas existentes |

Extrair conforme necessário:

- `FillMarkActionDock`, `FillMarkSizePanel`, `FillMarkColorPanel`, `FillMarkFormatPanel`.
- `EditorPropertyPanel` para apresentação, sem concentrar lógica de todas as ferramentas.
- `EditorNumericControl` para valor/stepper/erro, com política de faixa e unidade fornecida por cada ferramenta.
- Estado pequeno de comparação e histórico local por sessão.
- Componente de contexto de página/escopo que não altera o contrato de seleção.

Reutilizar `PdfPageGeometry`, `DocumentLayoutGestures`, `DocumentCaptureCoordinates`, helpers de rascunho/correção e renderizadores existentes. Não criar uma segunda matemática só para fazer a prévia parecer correta.

## 15. Sequência de implementação

### Entrega A — Consolidar a experiência antes de conectar eventos

1. Conferir diferenças do código posteriores a este plano e preservar alterações aceitas.
2. Capturar baseline com documentos sintéticos: texto selecionado, marca, teclado, três painéis de estilo, Ajustar e cada modo de Corrigir.
3. Criar/atualizar previews representativas em 320/390 dp e fonte 2×.
4. Montar dock, painel e controles numéricos com estado de preview.
5. Conferir se ações e rótulos cabem antes de migrar lógica.

**Saída:** composição visual revisável e inventário de cada controle antigo para seu novo destino. Não encerrar a implementação com apenas essa entrega.

### Entrega B — Preenchimento completo

1. Reorganizar Tamanho, Cor e Formato; separar Caixa de texto, Posição e Repetir.
2. Tornar Editar texto explícito e preservar estado inline.
3. Implementar transições IME ↔ painel sem reabertura automática.
4. Implementar histórico local/commit atômico, nova conclusão e mudança de página com rascunho válido.
5. Remover controles e confirmações antigas duplicadas somente depois de conectar os substitutos.
6. Validar Check/X, reedição, duplicar, excluir, reverter e exportação.

**Dependência:** A. **Saída:** preencher e estilizar um formulário sem procurar comandos escondidos ou confirmar cada propriedade.

### Entrega C — Ajustar completo

1. Migrar para Posição/Tamanho/Girar.
2. Unificar margem/enquadramento com miniaturas e estado Personalizado.
3. Manter precisão numérica, alcance de bordas e escopo atual.
4. Remover centralização por toque na alça.
5. Integrar histórico local, restaurar por seção e comparação.
6. Conferir que aplicar só altera os campos de layout pertinentes.

**Dependência:** A; pode ocorrer em paralelo a B com propriedade de arquivos definida.

### Entrega D — Corrigir completo

1. Reorganizar controles e alternância Editar/Ver resultado.
2. Tornar a detecção de contorno uma ação explícita e cancelável.
3. Expor valores finos de inclinação/curvatura e conversão quando relevante.
4. Implementar histórico local e comparação com baseline correto.
5. Isolar mudança da pinça do recorte e validar transformações.
6. Preservar página âncora, máscara, correções acumuladas e resultados correlacionados.

**Dependência:** A e base de comparação/histórico de C, sem acoplar seus modelos de ajuste.

### Entrega E — Animações e revisão inclusiva

1. Adicionar os movimentos da seção 11 aos estados funcionais.
2. Verificar ausência de salto do canvas com gesto, painel e IME.
3. Conferir tema, fonte, RTL, TalkBack e movimento reduzido.
4. Revisar truncamento, foco, valores e indisponibilidade em tela pequena.

**Dependência:** B–D. Motion não substitui a entrega funcional.

### Entrega F — Verificação e registro

1. Rodar testes unitários focados de `:app` durante a implementação.
2. Após integração, rodar suíte pertinente e build completo do aplicativo.
3. Consultar consumidores reais do módulo antes de ampliar builds; pedir preferência antes de testes extensivos em consumidores.
4. Com dispositivo disponível, executar testes de UI e jornadas com PDFs de teste.
5. Comparar prévia e arquivo exportado/reaberto.
6. Registrar comandos, resultados, evidências e limitações reais; atualizar status deste documento.

## 16. Validação funcional e visual

### Testes a reaproveitar/ampliar

- `FillMarkEditingTest`, testes de seleção, dimensionamento, tipografia e `PdfOverlayLayerTest`: estilos, conversão Check/X, posição, limites e fidelidade.
- `DocumentToolsViewModelTest`: commit único, undo/redo, cancelamento e não duplicação.
- `PdfPageGeometryTest`, `DocumentLayoutGesturesTest`, `DocumentLivePageGeometryTest`: âncoras, margens, escala, rotação e viewport.
- Testes existentes de rascunho/análise/captura de correções: escopo por página, preservação de campos e descarte de resposta antiga.
- Novos testes de comportamento do histórico local, entrada numérica e coordenação com o workspace.
- Testes Compose dos caminhos completos: não apenas verificar se um botão existe.

### Jornadas obrigatórias

1. **Estilo de texto:** inserir nome → tamanho 16 → azul → negrito/itálico/sublinhado → editar no meio do texto → abrir Cor → voltar ao teclado → concluir → salvar/reabrir. Conferir cursor, posição e aparência.
2. **Marcas:** inserir Check em sequência → selecionar uma → converter em X → redimensionar → mudar cor → duplicar → excluir/desfazer. Nenhuma marca extra é criada ao desmarcar.
3. **Rascunho:** editar elemento → desfazer/refazer local → trocar página → desfazer no workspace → reeditar. Não perder texto nem confirmar duas vezes.
4. **Telas pequenas:** repetir estilo com IME, 320 dp/fonte 2×, tema escuro. Ações essenciais continuam alcançáveis.
5. **Ajustar:** posição em cada canto → escala → três enquadramentos → girar → comparar → restaurar só tamanho → desfazer → aplicar nas páginas escolhidas.
6. **Recortar:** mover quatro cantos/bordas → pinça para inspeção sem alterar recorte → ver resultado → editar novamente → comparar → aplicar.
7. **Fundo:** iniciar detecção → cancelar → tentar novamente → ajustar manualmente → alternar modos enquanto chega resposta antiga. Ajuste manual não é perdido.
8. **Inclinação/curvatura:** slider → valor numérico localizado → desfazer → zerar só a seção → comparar. Testar limites e entrada inválida.
9. **PDF digital:** executar correção com/sem decisão de conversão permitida; conferir seleção de texto e dependência de filtros anteriores.
10. **Saída e falha:** Back, rotação, erro de prévia, erro de aplicar e reentrada. Estado fica recuperável; nenhuma mensagem anuncia sucesso sem aplicação.
11. **Limites da edição:** desfazer com composição IME ativa → esgotar histórico local → desfazer global → selecionar novamente. Testar Concluir com tamanho numérico pendente válido/inválido, Back, troca de categoria e preset sem dupla confirmação.
12. **Direção do texto:** editar conteúdo LTR e RTL → alternar Início/Centro/Fim → exportar/reabrir. Rótulo, medição, prévia e PDF devem concordar.

Usar PDF escaneado e digital, páginas com proporções diferentes, CropBox/rotação, conteúdo nos cantos e zoom baixo/alto. Usar somente arquivos sintéticos sem dados pessoais para jornadas que salvam ou substituem documentos.

### Avaliação de compreensão

Solicitar a participantes representativos tarefas sem indicar os botões: “deixe este nome azul e em negrito”, “troque esta marca por X”, “alinhe no topo”, “retire uma borda” e “veja se ficou melhor”.

Registrar hesitações, toques em confirmação errada, ações não encontradas e erros recuperados. As metas de toques deste plano orientam o desenho, mas não são evidência de usabilidade por si só.

## 17. Critérios de aceite da entrega

- [x] Tamanho, Cor e Formato estão identificáveis e acessíveis sem overflow.
- [ ] Check/X não se confundem com confirmar/cancelar elemento.
- [ ] Não há paleta, formulário de tamanho ou ação final duplicada.
- [ ] Abrir/fechar painel não reabre o IME sem intenção.
- [ ] Texto, seleção/cursor, ID, posição, zoom e estilo são preservados; finalizar composição IME não perde caracteres.
- [ ] Concluir resolve corretamente números pendentes; buffer inválido não é aplicado nem descartado silenciosamente.
- [ ] Desfazer durante composição e transição ao histórico global não causam dupla operação ou seleção obsoleta.
- [ ] Início/Centro/Fim preservam semântica e aparência em texto LTR/RTL.
- [ ] Rascunho válido permite trocar página/concluir sem confirmação redundante.
- [ ] Histórico local e global têm fronteira testada; commit não é duplicado.
- [x] Ajustar reúne enquadramento/margem/escala sem mudanças implícitas ao abrir controles.
- [x] Tocar na alça de mover não centraliza por acidente.
- [x] Topo, esquerda e cantos continuam alcançáveis conforme geometria/margem.
- [ ] Corrigir preserva página âncora e não introduz aplicação múltipla.
- [x] Detectar contorno é explícito, cancelável e não sobrescreve edição recente.
- [ ] Dois dedos navegam em todos os modos; recorte não muda por pinça.
- [ ] Antes/Depois usa baseline correto e mantém enquadramento.
- [x] Restaurar seção não apaga outras alterações.
- [ ] Conversão para imagem só ocorre conforme escolha/regras reais, com consequência visível.
- [ ] Animações não atrasam operações nem deslocam a página sob o dedo.
- [ ] Ações funcionam em 320 dp/fontes grandes, TalkBack e movimento reduzido.
- [ ] Prévia e PDF exportado/reaberto mantêm a mesma aparência.
- [ ] Fluxo de salvar existente, incluindo nome/tags quando cabível, permanece íntegro.
- [x] Testes, builds e verificações em dispositivo realizados estão documentados.

## 18. Instrução para a IA executora

> Implemente este plano no código atual do Scaniqo usando as ferramentas do Android Studio e as skills pertinentes. Preserve alterações aceitas e contratos de geometria, receita e salvamento.
>
> Priorize compreensão: Preencher tem Tamanho/Cor/Formato contextuais e escrita previsível; Ajustar reúne tamanho e margem; Corrigir permite editar, comparar e desfazer tentativas. Entregue cada fluxo com estado e eventos conectados.
>
> Remova duplicações e confirmações antigas apenas quando o comportamento substituto estiver completo. Não resolva falta de espaço ocultando os comandos essenciais em overflow, reduzindo alvos ou acrescentando mais rolagem horizontal.
>
> Implemente histórico local e commit atômico antes de simplificar confirmação. Não dispare Undo global para rascunhos não aplicados. Preserve cursor/IME, IDs, escopo e resultados correlacionados.
>
> Não adicione opções de fonte, margem, detecção ou correção sem suporte real. Reutilize geometria e renderização existentes; a prévia precisa representar o PDF salvo.
>
> Faça animações curtas ligadas ao estado real, valide com arquivos sintéticos e registre testes/resultados/limitações. Só marque os critérios concluídos com evidência; componentes criados, isoladamente, não significam experiência entregue.

## 19. Registro da implementação — 25/10/2026

- **Preencher:** controles contextuais Tamanho/Cor/Formato, entrada numérica única, alternância explícita com teclado, histórico local e conclusão textual. A seleção revela brevemente contorno e alças sem deslocar a área de toque. A prévia de 320 dp com fonte 1,5× foi inspecionada e o painel compacto foi ajustado para não quebrar os rótulos. O teste `FillMarkControlsInteractionTest` confirmou Azul e Negrito no mesmo elemento, preservando ID, tipo e conteúdo.
- **Ajustar:** Posição/Tamanho/Girar, presets de enquadramento com margem no painel Tamanho, escala editável, Antes/Depois, restauração por categoria e desfazer/refazer locais. O teste `LayoutPositionInteractionTest` percorreu as nove âncoras, confirmou topo esquerdo e verificou que tocar na alça não recentraliza.
- **Corrigir:** modos Recortar/Fundo/Inclinar/Curvatura com controles visíveis, detecção de fundo explícita e cancelável, comparação, histórico local, entrada numérica e pinça reservada ao zoom. `CorrectionInteractionTest` confirmou que a análise só inicia após o comando e que a pinça não altera o recorte. As prévias de recorte e inclinação com fonte 1,3× foram inspecionadas.
- **Integração:** `DocumentToolScreenLayout` mantém documento e controle principal acessíveis em pouca altura e usa painel lateral em paisagem larga. A topbar expõe Concluir/Aplicar por nome; os eventos existentes continuam aplicando as receitas ao workspace. As novas strings foram incluídas no catálogo base e nas dez localidades cobertas por `LocalizationResourcesTest`.

**Verificação executada:** `:app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin` passou; `:app:connectedDebugAndroidTest` passou com filtro para `CorrectionInteractionTest` e `LayoutPositionInteractionTest` (3 testes) e, em seguida, para `FillMarkControlsInteractionTest` (1 teste), no emulador Pixel_10_Pro. Após a animação de seleção, `:app:testDebugUnitTest --tests com.joseleandro.scaniqo.ui.documentEditor.FillMarkEditingTest` e `:app:assembleDebug` também passaram. `git diff --check` passou. A inspeção visual direta cobriu Preencher e Corrigir em larguras de 320–360 dp e fonte ampliada.

**Ainda a validar:** jornadas completas com PDFs sintéticos escaneados e digitais, exportação/reabertura, teclado e composição IME em condições reais, fonte 2×, TalkBack, RTL e movimento reduzido. A prévia Compose de Ajustar no IDE ainda carrega um bytecode antigo e mostra `IllegalAccessError` em `LayoutDraftHistory.Companion`; o APK recompilado contém a `Companion` pública e o teste instrumentado da tela passou no emulador. Os itens correspondentes acima seguem desmarcados até essa validação.
