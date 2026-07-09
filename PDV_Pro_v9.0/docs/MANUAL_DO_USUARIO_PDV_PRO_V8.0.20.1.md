# Manual do Usuário - PDV Pro
**Versão do sistema:** v8.0.20.1
**Data de emissão:** 19/06/2026

Guia profissional de implantação, operação e administração

## Sumário
- 1. Visão Geral
- 2. Primeira Implantação
- 3. Acesso, Sessão e Licença
- 4. Painel Principal
- 5. Cadastros Essenciais
- 6. Produtos e Estoque
- 7. Caixa
- 8. Vendas e Pagamento
- 9. Comandas
- 10. Mesas, Cardápio QR e Cozinha
- 11. Armários de Sauna
- 12. Ordem de Serviço
- 13. Delivery, Entregas e WhatsApp
- 14. Contas a Receber
- 15. Relatórios e Histórico
- 16. Painéis de Atendimento e Senhas
- 17. Estacionamento
- 18. Impressoras e Multiimpressoras
- 19. Backup, Atualização e Diagnóstico
- 20. Segurança, Permissões e Auditoria
- 21. Rotina Diária Recomendada
- 22. Suporte e Contato

---

## 1. Visão Geral

O PDV Pro é uma suíte comercial para operação de ponto de venda, controle financeiro, estoque, atendimento em mesas, delivery, cozinha, WhatsApp, ordens de serviço, estacionamento, armários de sauna, relatórios, impressoras e rotinas administrativas. O sistema foi desenhado para negócios que precisam vender rápido, controlar o caixa e manter rastreabilidade das operações.

Este manual foi escrito para implantação, treinamento e uso diário. Ele segue a organização real do aplicativo: primeiro a preparação do ambiente, depois cadastros, operação de venda, módulos especializados, controles administrativos e boas práticas.

### Passo a passo

1. Instale o aplicativo e confirme se o servidor MySQL/MariaDB está disponível.

2. Configure o banco de dados e teste a conexão antes de cadastrar dados.

3. Ative ou renove a licença para liberar a operação.

4. Entre com o usuário administrador inicial, crie usuários reais e revise permissões.

5. Cadastre empresa, formas de pagamento, categorias, produtos, clientes e operadores.

6. Configure a impressora, abra o caixa e execute uma venda de teste.

7. Ative módulos adicionais conforme o tipo de negócio: mesas, comandas, delivery, WhatsApp, cozinha, OS, estacionamento ou armários.

### Observações

- **Credenciais iniciais:** Na primeira base, o sistema cria o usuário padrão admin com senha admin. A senha administrativa usada em operações sensíveis é 4872. Recomenda-se trocar a senha do usuário administrador logo após a implantação e restringir o conhecimento da senha administrativa a responsáveis autorizados.

## 2. Primeira Implantação

A implantação correta evita falhas de venda, impressão e backup durante o atendimento. Faça a configuração inicial em um momento sem movimento de caixa, com internet e rede local estáveis.

### Passo a passo

1. Abra o PDV Pro e aguarde a tela de inicialização finalizar a preparação do ambiente.

2. O PDV Pro inicia automaticamente o MariaDB interno. Aguarde a inicialização do banco local.

3. A configuração padrão já vem pronta: host 127.0.0.1, porta 3306, banco banco, usuário usuario e senha senha.

4. Se precisar usar outro servidor, toque em Configurar Banco de Dados, informe os dados e use Testar Conexão.

5. Toque em Salvar e volte para o login quando alterar a configuração.

6. Use Servidor MySQL para verificar o MariaDB integrado. Não é necessário instalar PHSERVER separado.

7. Use Criar Banco quando precisar criar a base inicial, e Usuários MySQL quando precisar gerenciar credenciais do banco.

8. Entre no sistema, acesse Licença e informe chave, contra-chave e data de expiração conforme o contrato.

9. Acesse Atualizar tabelas e colunas do banco de dados quando migrar uma base antiga para esta versão.

### Observações

- **Compatibilidade Android:** Esta edição inclui MariaDB integrado para ARM64 e ARM de 32 bits. Ela é compatível com smartphones Android novos de 64 bits e mantém suporte aos aparelhos ARM anteriores.

- **Permissões do Android:** Conceda câmera para leitor de código de barras, Bluetooth/USB/rede para impressoras, armazenamento para backup, notificações/acessibilidade para WhatsBot e localização para modo entregador. Sem essas permissões, as funções relacionadas ficam limitadas.

- **Rede local:** Cardápio QR Code, cozinha web, senhas web e painéis locais dependem de o celular/tablet e os demais dispositivos estarem na mesma rede Wi-Fi.

## 3. Acesso, Sessão e Licença

A tela de login centraliza entrada de usuários, banco, senha, recuperação e licença. O usuário seleciona o nome no campo de usuário, digita a senha e toca em Acessar Sistema.

### Passo a passo

1. Para entrar: selecione o usuário, digite a senha e toque em Acessar Sistema.

2. Para mostrar ou ocultar a senha: use o botão de olho ao lado do campo de senha.

3. Para trocar a própria senha: toque em Trocar Senha, informe senha atual, nova senha e confirmação.

4. Para recuperar senha: toque em Esqueci minha senha, escolha o usuário, selecione um administrador, peça a senha do administrador, informe a nova senha e confirme.

5. Para inserir nova licença: toque em Inserir nova licença, autentique com senha administrativa e preencha a tela de ativação.

6. Para trocar de usuário depois de logado: no painel principal, toque em Trocar Usuário.

7. Para encerrar o app: toque em Sair do App.

### Observações

- **Alerta de vencimento:** Quando a licença está a 5 dias ou menos do vencimento, o painel exibe aviso. Renove antes da data final para evitar bloqueio da operação.

## 4. Painel Principal

O painel principal organiza as funções por blocos: Operacional, Gestão e Cadastros, Delivery e WhatsApp, Configurações e Sistema, e Outros Recursos. Os botões exibidos dependem do perfil e das permissões do usuário.

A tabela abaixo resume todas as funções visíveis no menu principal e o papel de cada uma no negócio.

| Função | Uso principal |

| --- | --- |

| Vendas | Venda direta de balcão com cliente, vendedor, entregador, desconto, acréscimo e observação. |

| Comandas | Abertura, edição, impressão, cancelamento e fechamento de comandas. |

| Caixa | Abertura de caixa, lançamento de vales/débitos, conferência e fechamento. |

| Gerenciar Mesas | Mapa visual de mesas com ocupação, reserva, pedidos, impressão e pagamento. |

| Gerenciar Armários | Controle de armários de sauna, entrega/devolução de chave, consumo e pagamento. |

| Ordem de Serviço | OS com cliente, equipamento, defeitos, soluções, serviços, produtos, fotos e impressão. |

| Estoque | Consulta, filtros, edição, ativação/inativação e manutenção dos produtos cadastrados. |

| Novo Produto | Cadastro completo de produto com código, EAN, tipo, preço, estoque e foto. |

| Clientes | Cadastro de dados pessoais, endereço, bairro, contato e uso em vendas/recebimentos. |

| Histórico | Últimas vendas, reimpressão, envio por WhatsApp, cancelamento e devolução. |

| Relatórios | Vendas, lucratividade, vendedor, entregador, cliente, produtos, caixa e contas a receber. |

| Contas a Receber | Recebimento parcial/total, extrato por cliente, histórico e cancelamento de conta. |

| Entrada Notas | Registro de notas de entrada, itens, confirmação e atualização de estoque. |

| Vendedores | Cadastro de vendedores ativos para vínculo com vendas e relatórios. |

| Entregadores | Cadastro de entregadores usados em delivery e rastreamento. |

| Bot WhatsApp | Atendimento automático, catálogo, pedidos, IA, mensagens, logs e permissões. |

| Gerenciar Entregas | Acompanhamento de pedidos para entrega, status, entregador, cupom e WhatsApp. |

| Taxas de Entrega | Cadastro de bairros e taxas para venda de entrega e pedidos via WhatsApp. |

| Modo Entregador | Ativação do rastreamento GPS em segundo plano para entregadores. |

| Empresa | Dados comerciais, fiscais e de contato da empresa usados em documentos e cupons. |

| Usuários | Criação e manutenção de usuários com login, senha e perfil de acesso. |

| Permissões | Perfis, permissões por módulo, personalização de dashboard e exceções por usuário. |

| Impressora | Configuração de impressora por rede, Bluetooth, USB, Windows, Print Server e IP direto. |

| Multi Impressoras | Regras por categoria para imprimir pedidos em cozinha, bar, forno ou setores. |

| Backup | Backup JSON/SQL por FTP, restauração, backup automático e zona de perigo. |

| Diagnóstico Pro | Central de saúde do sistema: app, banco, licença, backup, permissões e armazenamento. |

| Atualizar Sistema | Busca de APK no FTP configurado e instalação da atualização. |

| Licença | Ativação, renovação, contra-chave, vencimento e limpeza da tabela de licença. |

| Senha | Troca de senha do usuário autenticado. |

| Servidor MySQL | Inicialização/verificação do MariaDB integrado ao PDV Pro, sem APK separado. |

| Criar Banco | Criação do banco de dados PDV Pro via helper MySQL. |

| Usuários MySQL | Gerenciamento de usuários do banco MySQL. |

| Pagamentos | Cadastro das formas de pagamento: dinheiro, crédito, débito, PIX e contas a receber. |

| Categorias | Categorias/tipos de produto usados em produtos, adicionais e multiimpressoras. |

| Adicionais | Complementos vinculáveis aos tipos de produto, com preço próprio. |

| Obs. Cupom | Observações padrão para cupom e atendimento. |

| Painel Chamados | Painel web/local de chamadas de clientes com voz. |

| Senhas Web | Painel web de senhas para exibição externa. |

| Gerenciar Chamados | Gerenciador de chamadas com voz, sinal sonoro, ajuste de fala e vibração. |

| Garçons | Cadastro de garçons para vínculo com mesas. |

| Cadastro Mesas | Cadastro unitário de mesas com número, descrição e capacidade. |

| Cardápio QR Code | Geração de QR Code por mesa e painel de pedidos web. |

| Painel Cozinha | WebView/servidor local para pedidos de cozinha com alertas sonoros. |

| Web Cozinha | Acesso web ao painel da cozinha pela rede local. |

| Cadastro Armários | Cadastro de armários de sauna. |

| Cadastro Serviços | Cadastro de serviços usados em ordens de serviço. |

| Trocar Usuário | Encerra a sessão atual e retorna para login. |

| Sair do App | Fecha o aplicativo. |

| Sobre | Dados de versão, desenvolvedor e contato. |

### Observações

- **Botão oculto ou desativado:** Se um botão não aparecer ou estiver sem acesso, revise Perfis e Permissões. O sistema separa a permissão de exibir o botão no dashboard da permissão de acessar o módulo.

## 5. Cadastros Essenciais

Os cadastros são a base da operação. Quanto melhor a preparação, mais rápida fica a venda e mais confiáveis ficam relatórios, recebimentos, delivery e impressão.

### Empresa

1. Acesse Empresa.

2. Toque em Novo ou edite o registro existente.

3. Preencha razão social, nome fantasia, CNPJ, inscrição estadual, endereço, número, bairro, cidade, UF, CEP, telefone e e-mail.

4. Salve. Esses dados são usados em documentos comerciais, relatórios e impressão.

### Clientes

1. Acesse Clientes e toque em + Novo.

2. Preencha nome, CPF/CNPJ, celular, e-mail, endereço, número, bairro, cidade, UF e CEP.

3. Use o bairro cadastrado em Taxas de Entrega quando o cliente for usado em delivery.

4. Salve. Para remover da operação, inative em vez de apagar histórico.

### Vendedores, entregadores e garçons

1. Acesse o cadastro correspondente.

2. Toque em + Novo, informe nome e demais dados solicitados.

3. Salve e mantenha apenas pessoas ativas que devem aparecer em vendas, mesas e entregas.

### Usuários

1. Acesse Usuários.

2. Toque em + Novo.

3. Informe nome, login, senha e perfil de acesso.

4. Salve. Use uma conta por pessoa para preservar auditoria de caixa, OS, mesas e permissões.

### Perfis e permissões

1. Acesse Permissões.

2. Crie ou edite um perfil.

3. Abra Permissões e marque as ações liberadas por módulo.

4. Use Personalizar para decidir quais botões aparecem no dashboard do perfil.

5. Use permissões por usuário apenas como exceção pontual, mantendo o perfil como regra principal.

### Formas de pagamento

1. Acesse Pagamentos.

2. Cadastre dinheiro, PIX, crédito, débito, contas a receber e outras formas usadas no negócio.

3. Defina se a forma permite parcelamento quando aplicável.

4. Mantenha ativa apenas a forma que deve aparecer no fechamento de venda.

### Categorias, adicionais e observações

1. Em Categorias, cadastre grupos como Bebidas, Lanches, Serviços ou Peças.

2. Em Adicionais, cadastre complementos com valor e vincule ao tipo de produto quando usado.

3. Em Obs. Cupom, cadastre mensagens ou observações padrão para impressão e atendimento.

### Mesas, armários e serviços

1. Em Cadastro Mesas, crie mesas individuais com número, descrição e capacidade.

2. Em Gerenciar Mesas, use o Assistente para criar intervalos grandes de mesas de uma só vez.

3. Em Cadastro Armários, crie os armários de sauna.

4. Em Cadastro Serviços, cadastre serviços usados em ordens de serviço.

## 6. Produtos e Estoque

Produtos alimentam vendas, comandas, mesas, armários, OS, cardápio digital, WhatsBot e relatórios. O cadastro completo reduz erro de preço, estoque e impressão.

### Cadastrar novo produto

1. Acesse Novo Produto.

2. Toque em + Novo.

3. Preencha código interno, código de barras/EAN, descrição, tipo do produto, unidade, preço de custo, preço de venda, estoque e estoque mínimo.

4. Use o gerador de EAN-13 quando precisar criar código de barras válido.

5. Inclua foto quando desejar exibir produto com imagem.

6. Salve e teste a busca pelo código/descrição na tela de venda.

### Gerenciar estoque

1. Acesse Estoque.

2. Use a busca por descrição ou código.

3. Alterne filtros Todos, Ativos e Inativos.

4. Toque no produto para editar dados, gerar EAN, alterar estoque ou inativar/reativar.

5. Produtos inativos deixam de aparecer nas vendas, mas preservam histórico.

### Entrada de notas

1. Acesse Entrada Notas e toque em + Nova.

2. Informe fornecedor e dados da nota.

3. Adicione itens da nota, selecionando produto, quantidade e valores.

4. Revise os itens.

5. Confirme a nota para atualizar estoque.

6. Cancele somente notas pendentes quando houver erro antes da confirmação.

### Observações

- **Controle de custo:** O relatório de lucratividade depende de preço de custo preenchido corretamente. Revise custos sempre que houver mudança de fornecedor.

## 7. Caixa

O caixa é a trava operacional da venda. Vendas, fechamento de comanda, mesa, armário e estacionamento precisam de caixa aberto para seguir de forma consistente.

### Passo a passo

1. Acesse Caixa no painel principal.

2. Se estiver fechado, toque em Abrir Caixa.

3. Informe o valor inicial em dinheiro.

4. Durante o expediente, use + Vale de Débito para registrar retiradas, adiantamentos ou débitos internos.

5. Ao finalizar o turno, toque em Fechar Caixa.

6. Confira vendas, vales/débitos, total esperado e valor de fechamento.

7. Finalize o fechamento e imprima ou envie o comprovante quando necessário.

### Observações

- **Rotina recomendada:** Abra um caixa por turno ou por responsável. Evite compartilhar caixa entre operadores sem controle, pois isso dificulta conferência e auditoria.

## 8. Vendas e Pagamento

A venda direta atende balcão, varejo e pedidos rápidos. O fluxo normal é selecionar cliente/vendedor, adicionar produtos, ajustar desconto/acréscimo e finalizar no pagamento.

### Criar venda

1. Acesse Vendas.

2. Selecione Cliente quando desejar identificar a venda ou usar Contas a Receber.

3. Selecione Vendedor e Entregador quando aplicável.

4. Busque produto por código de barras, código interno ou descrição.

5. Use o scanner de código de barras quando a câmera estiver liberada.

6. Informe quantidade e escolha adicionais se o tipo de produto possuir complementos.

7. Revise carrinho, desconto, acréscimo, observação e total.

8. Toque em Finalizar Venda.

### Receber pagamento

1. Na tela Pagamento, toque em + Adicionar.

2. Escolha a forma de pagamento.

3. Informe o valor recebido. O sistema calcula valor pago, restante e troco.

4. Use Dividir quando o cliente pagar em mais de uma forma.

5. Para Contas a Receber, volte e selecione um cliente antes de adicionar essa forma.

6. Marque Para Entrega quando a venda for delivery e selecione o bairro para aplicar taxa.

7. Escolha opções de impressão: canhoto de senha, duas vias do cupom e senha no cupom.

8. Toque em Finalizar Venda e, se desejar, imprima o cupom.

### Regras importantes

1. Não é possível finalizar venda com carrinho vazio.

2. Não é possível concluir pagamento com saldo pendente.

3. Contas a Receber só é permitido uma vez por venda.

4. Ao finalizar, o sistema baixa estoque, grava pagamentos, gera conta a receber quando necessário e encerra comanda, mesa, armário, OS ou estacionamento vinculado.

## 9. Comandas

Comandas organizam consumo por número, cliente e observação. São ideais para bares, lanchonetes e atendimento onde o cliente consome antes de pagar.

### Passo a passo

1. Acesse Comandas.

2. Toque em + Nova Comanda.

3. Informe número, cliente e observação se necessário.

4. Abra a comanda para adicionar produtos.

5. Busque produtos por código, descrição ou scanner.

6. Informe quantidade e observação do item.

7. Use Imprimir para entregar conferência ou pedido.

8. Toque em Fechar e Pagar para enviar a comanda ao pagamento.

9. Após pagamento, a comanda é marcada como fechada.

10. Use Cancelar Comanda apenas quando realmente desejar inutilizar uma comanda aberta.

## 10. Mesas, Cardápio QR e Cozinha

O módulo de mesas cobre restaurantes e atendimento salão. Ele possui mapa visual, reserva, garçom, quantidade de pessoas, itens, adicionais, observação para cozinha, impressão e pagamento.

### Criar e preparar mesas

1. Cadastre mesas em Cadastro Mesas ou use Assistente em Gerenciar Mesas.

2. No Assistente, informe número inicial, número final, capacidade e prefixo de descrição.

3. Escolha se deseja sobrescrever mesas existentes.

4. Confirme para criar, atualizar ou ignorar mesas conforme o caso.

### Operar uma mesa

1. Acesse Gerenciar Mesas.

2. Toque em uma mesa livre, ocupada ou reservada.

3. Informe garçom, quantidade de pessoas e produtos.

4. Adicione produtos por lista ou leitor de código de barras.

5. Informe adicionais e observações para cozinha quando necessário.

6. Salve a mesa. Itens novos também podem ser salvos automaticamente conforme a operação.

7. Imprima pedido completo ou último item para produção.

8. Marque como pronta para cobrança quando a conta puder ser fechada.

9. Envie para pagamento e conclua na tela Pagamento.

### Reservas

1. Abra a mesa e use a opção de reservar.

2. A mesa reservada fica vinculada ao usuário que reservou.

3. Somente o usuário dono da reserva, ou perfil com permissão adequada, deve alterar a mesa reservada.

4. Cancele a reserva para liberar a mesa ou transformar em ocupada conforme os itens existentes.

### Cardápio QR Code

1. Acesse Cardápio QR Code.

2. Aguarde o servidor local iniciar.

3. Selecione a mesa.

4. Toque em Gerar QR Code.

5. Imprima ou exiba o QR Code na mesa.

6. O cliente escaneia, abre o cardápio no navegador e envia o pedido.

7. Os pedidos aparecem no painel e os itens são adicionados à mesa correspondente.

### Painel da Cozinha e Web Cozinha

1. Acesse Painel Cozinha no dispositivo que ficará na produção.

2. Use Web Cozinha para exibir o painel em outro aparelho da mesma rede.

3. Mantenha o servidor local aberto durante o atendimento.

4. Use os alertas sonoros e visuais para acompanhar pedidos novos, prontos e urgentes.

## 11. Armários de Sauna

O módulo de armários controla entrega de chave, consumo por armário, manutenção e pagamento. Ele é útil para clubes, saunas, academias e operações por chave.

### Passo a passo

1. Cadastre os armários em Cadastro Armários.

2. Acesse Gerenciar Armários.

3. Use a busca por número ou toque diretamente no armário.

4. Para iniciar uso, informe o cliente e toque em Entregar Chave.

5. Adicione produtos consumidos no armário por categoria, lista ou código de barras.

6. Salve os itens e imprima pedido completo ou último item quando necessário.

7. Para cobrar, envie o armário para Pagamento.

8. Após pagamento, o sistema encerra o uso e devolve a chave.

9. Use Manutenção quando o armário estiver indisponível e Liberação de Manutenção quando puder voltar à operação.

## 12. Ordem de Serviço

A ordem de serviço combina atendimento técnico e comercial. Ela registra cliente, equipamento, defeitos, soluções, serviços, produtos, fotos, desconto, responsável e impressão.

### Passo a passo

1. Acesse Ordem de Serviço.

2. Toque em + Novo.

3. Selecione ou informe o cliente.

4. Preencha equipamento/aparelho, defeito relatado, detalhes técnicos, defeitos, soluções e observações.

5. Adicione serviços cadastrados e produtos usados.

6. Inclua fotos pela câmera ou galeria para formar dossiê técnico.

7. Revise totais, desconto em valor ou percentual e status.

8. Salve a OS.

9. Use Imprimir para entregar comprovante ou relatório ao cliente.

10. Use WhatsApp para enviar a OS com ou sem número informado.

11. Quando concluída, envie para pagamento. O sistema cria a venda vinculada e marca a OS como concluída ao receber.

### Observações

- **Fotos e FTP:** As fotos podem ser sincronizadas com FTP. Se a foto local não estiver disponível, o detalhe da OS tenta recuperar a cópia remota quando houver configuração e rede.

## 13. Delivery, Entregas e WhatsApp

O PDV Pro integra venda para entrega, taxa por bairro, entregador, status operacional, notificação automática e rastreamento por GPS.

### Taxas de entrega por bairro

1. Acesse Taxas de Entrega.

2. Toque em + Adicionar Bairro.

3. Informe bairro e valor da taxa.

4. Salve. O bairro passa a aparecer em clientes e na tela de pagamento para entrega.

5. Edite, inative, reative ou exclua conforme a política comercial.

### Venda para entrega

1. Na venda, selecione cliente, produtos e finalize.

2. Na tela Pagamento, toque em Para Entrega.

3. Selecione o bairro para aplicar a taxa.

4. Confira endereço do cliente e total.

5. Finalize o pagamento.

6. A entrega ficará disponível em Gerenciar Entregas.

### Gerenciar entregas

1. Acesse Gerenciar Entregas.

2. Filtre por Todas, Pendentes, Em Rota, Entregues ou Canceladas.

3. Busque por ID, cliente ou entregador.

4. Abra uma entrega para ver detalhes, itens, pagamentos e endereço.

5. Altere status para acompanhar a operação.

6. Atribua ou remova entregador.

7. Reimprima cupom, envie cupom por WhatsApp, responda cliente ou envie localização.

8. Cancele entrega somente quando houver justificativa operacional.

### Modo Entregador

1. Acesse Modo Entregador no aparelho do entregador.

2. Selecione o entregador.

3. Toque em Ativar Rastreamento GPS.

4. Conceda localização, inclusive em segundo plano quando solicitado.

5. Mantenha o rastreamento ativo durante a rota.

6. Toque em Desativar Rastreamento ao encerrar o turno.

### WhatsBot

1. Acesse Bot WhatsApp.

2. Conceda permissão de acesso a notificações e habilite o serviço do PDV Pro no Android.

3. Configure nome da empresa, horário de atendimento, número admin, delay e limite de produtos.

4. Ative funcionalidades: catálogo, consulta de preço, pedidos, cupom automático, notificação ao admin, grupos, apenas contatos salvos, impressão automática e logs.

5. Opcionalmente, ative IA informando chave API, URL, modelo e recursos de interpretação.

6. Personalize mensagens de boas-vindas, fora do horário e encerramento.

7. Salve configurações, teste o bot e acompanhe os logs.

### Observações

- **WhatsApp automático:** O envio automático depende do serviço de acessibilidade/controle estar habilitado no Android e do WhatsApp instalado e logado no aparelho.

## 14. Contas a Receber

Contas a Receber nasce quando uma venda é finalizada usando a forma de pagamento desse tipo. A rotina permite controle de pendência, recebimento parcial, recebimento total, extrato e histórico.

### Passo a passo

1. Acesse Contas a Receber.

2. Use filtros por cliente/status e toque em IR para carregar.

3. Abra uma conta para ver opções disponíveis.

4. Toque em Registrar Recebimento.

5. Escolha a forma de pagamento real usada pelo cliente, informe valor e observação.

6. Confirme. O sistema atualiza valor pago, valor pendente e status.

7. Use Extrato para ver todas as contas de um cliente.

8. Use Histórico de Recebimentos para auditar pagamentos feitos.

9. Cancele uma conta apenas quando a cobrança não deve mais existir.

## 15. Relatórios e Histórico

Relatórios consolidam desempenho comercial e financeiro. Histórico permite ações em vendas recentes, como reimprimir, enviar por WhatsApp, cancelar ou devolver.

### Relatórios disponíveis

1. Relatório de Vendas: últimas vendas com cliente, data, total e status.

2. Lucratividade por Produto: venda, custo e margem por produto.

3. Vendas por Vendedor: quantidade e total por vendedor.

4. Entregas por Entregador: desempenho por entregador.

5. Vendas por Cliente: ranking de clientes por compra.

6. Produtos Mais Vendidos: produtos ordenados por quantidade.

7. Fechamento de Caixa: resumo dos caixas recentes.

8. Contas a Receber: total original, recebido, pendente e clientes em aberto.

### Histórico de vendas

1. Acesse Histórico.

2. Abra uma venda.

3. Escolha ação disponível conforme sua permissão: reimprimir, enviar WhatsApp, cancelar ou devolver.

4. Cancelamento e devolução exigem autorização de administrador.

5. Na devolução, o sistema devolve estoque e cancela contas a receber pendentes vinculadas.

## 16. Painéis de Atendimento e Senhas

Os painéis web transformam o PDV em central de atendimento visual e sonora. Eles são úteis para balcões, retirada de pedidos, clínicas, oficinas e ambientes com chamada por senha.

### Painel de senhas

1. Acesse Painel de Senhas.

2. Use Chamar Próxima para chamar a próxima senha aguardando.

3. Selecione uma senha e use Chamar Selecionada quando precisar priorizar.

4. Use Atualizar para recarregar a fila.

5. Use Limpar para retirar chamadas da tela.

6. Use Zerar para reiniciar a sequência para 001.

### Senhas Web

1. Acesse Senhas Web.

2. Aguarde o painel web iniciar.

3. Exiba o endereço em outro dispositivo da mesma rede quando necessário.

### Painel e gerenciador de chamados

1. Acesse Painel Chamados para iniciar o servidor de chamados.

2. Use Gerenciar Chamados para o painel com voz, sinal sonoro, vibração e ajustes de fala.

3. Mantenha o dispositivo com volume adequado para chamadas audíveis.

## 17. Estacionamento

O módulo Estacionamento registra entrada, calcula permanência, imprime comprovante de chegada e envia a saída para pagamento.

### Passo a passo

1. Acesse Estacionamento.

2. Toque em Nova Entrada.

3. Informe placa, veículo, condutor, telefone, vaga, tipo, valor por hora e observação.

4. Use o leitor inteligente/voz de placa quando disponível.

5. Confirme Registrar Entrada. O sistema gera ticket.

6. Imprima o comprovante de chegada quando necessário.

7. Para saída, selecione o veículo aberto e toque em Finalizar Saída.

8. Revise o valor calculado e conclua na tela Pagamento.

9. Use Cancelar apenas para entrada indevida, pois o status ficará como cancelado.

10. Consulte o histórico do dia para entradas e saídas recentes.

## 18. Impressoras e Multiimpressoras

A impressão é crítica em PDV, cozinha, comandas, OS, estacionamento e recibos. Configure e teste antes da operação real.

### Configurar impressora principal

1. Acesse Impressora.

2. Escolha o tipo: Rede TCP/IP, Bluetooth, USB, Rede Windows SMB/CIFS, Rede Windows Direta, Bluetooth Windows, Servidor de Impressão, Rede IP Direto ou Nenhuma.

3. Preencha os dados do tipo escolhido: IP/porta, MAC Bluetooth, caminho Windows, credenciais, servidor ou porta.

4. Escolha tamanho do papel: 58mm ou 80mm.

5. Escolha driver/modelo mais próximo da impressora.

6. Teste a conexão.

7. Imprima página de teste.

8. Salve.

### Rede Windows e Print Server

1. Para SMB/CIFS, navegue na rede ou informe host e compartilhamento manualmente.

2. Para servidor de impressão, execute o PDV_Print_Server.py no computador Windows.

3. Informe IP do computador e porta padrão 9200.

4. Busque impressoras do PC, selecione a impressora instalada e salve.

### Multiimpressoras

1. Acesse Multi Impressoras.

2. Marque Ativar impressão em multiimpressoras.

3. Toque em + Adicionar Categoria.

4. Escolha tipo/categoria de produto.

5. Informe nome da impressora ou setor, como Cozinha, Bar ou Forno.

6. Escolha impressora instalada do PC/servidor ou configure tipo, IP, porta, MAC, papel e driver.

7. Salve a regra.

8. Use Testar em cada regra antes de abrir atendimento.

### Observações

- **Teste obrigatório:** Sempre envie página de teste e um pedido real simulado. Isso valida acentos, corte, largura do papel, QR Code, conexão e driver ESC/POS.

## 19. Backup, Atualização e Diagnóstico

Essas rotinas protegem dados e reduzem tempo de suporte. Configure backup antes de iniciar operação comercial definitiva.

### Backup e restauração

1. Acesse Backup.

2. Informe host FTP, usuário e senha.

3. Ative backup automático quando desejar cópia recorrente.

4. Marque incluir backup SQL quando precisar do dump MySQL além do JSON.

5. Toque em Salvar Configuração.

6. Use Fazer Backup Agora para JSON.

7. Use Fazer Backup SQL Agora para dump MySQL.

8. Use Listar Backups/Restaurar para consultar arquivos remotos e restaurar.

9. Digite a senha de segurança quando o sistema solicitar para restauração ou zona de perigo.

### Zona de perigo

1. Apagar Dados do Banco remove dados operacionais, preservando usuários quando aplicável.

2. Apagar Arquivos do FTP remove backups e arquivos remotos.

3. Use essas ações somente com autorização formal e backup validado.

### Atualizar sistema

1. Configure FTP na tela Backup.

2. Envie o arquivo PDV_Pro.apk para o FTP configurado.

3. Acesse Atualizar Sistema.

4. Confirme a verificação.

5. O app baixa o APK e solicita permissão para instalar de fontes desconhecidas quando necessário.

6. Conclua a instalação e abra novamente o PDV Pro.

### Diagnóstico Pro

1. Acesse Diagnóstico Pro antes de vender, atualizar ou prestar suporte.

2. Revise status de app, banco, licença, backup, permissões, armazenamento, servidor local e histórico de falhas.

3. Use Testar Conexão para confirmar banco.

4. Use Copiar Resumo para enviar evidências ao suporte.

## 20. Segurança, Permissões e Auditoria

A versão 8.0.20.1 usa permissões granulares por módulo, ações e botões do dashboard. Isso permite criar perfis por função, por exemplo operador de caixa, gerente, entregador, cozinha e administrador.

### Passo a passo

1. Crie perfis por função real do negócio.

2. Conceda apenas os módulos necessários para cada perfil.

3. Separe permissão de visualizar botão da permissão de acessar o módulo.

4. Evite usar usuário administrador no caixa diário.

5. Use permissões individuais apenas para exceções temporárias.

6. Revise permissões após cadastrar novos módulos ou atualizar o sistema.

7. Mantenha backup ativo antes de permitir cancelamentos, devoluções e zona de perigo.

### Observações

- **Boas práticas:** Usuários individuais, caixa por turno, permissões mínimas e backup recorrente formam a base de uma operação auditável.

## 21. Rotina Diária Recomendada

Use este roteiro como checklist de abertura, operação e fechamento. Ele reduz erro humano e deixa claro quem é responsável por cada etapa.

### Abertura

1. Ligar dispositivo, impressoras e computador/servidor quando usado.

2. Confirmar internet e Wi-Fi.

3. Confirmar no botão Servidor MySQL que o MariaDB integrado está ativo, se necessário.

4. Entrar no PDV com usuário do turno.

5. Verificar licença e Diagnóstico Pro.

6. Abrir caixa com valor inicial.

7. Imprimir página de teste quando houver troca de impressora ou papel.

8. Conferir produtos críticos e estoque.

### Durante a operação

1. Registrar todas as vendas no sistema.

2. Usar cliente identificado para contas a receber e delivery.

3. Salvar mesas/armários antes de imprimir ou cobrar.

4. Atualizar status de entregas.

5. Usar permissões de gerente para cancelamentos e devoluções.

6. Monitorar cozinha, senhas e WhatsBot quando ativos.

### Fechamento

1. Finalizar vendas pendentes, mesas, comandas, armários e estacionamento.

2. Conferir contas a receber geradas no dia.

3. Fechar caixa com valor real.

4. Imprimir ou enviar comprovante do fechamento.

5. Executar backup manual se houve grande movimento ou alteração cadastral importante.

6. Sair do usuário e fechar o aplicativo.

## 22. Suporte e Contato

Antes de acionar suporte, copie o resumo do Diagnóstico Pro, informe a versão exibida no rodapé do painel principal, descreva o módulo afetado e registre se o erro envolve banco, impressão, rede, licença ou permissão.

Dados exibidos na tela Sobre: desenvolvedor PHDA, telefone para contato (85) 98123-7727.

### Passo a passo

1. Abra Diagnóstico Pro e toque em Atualizar.

2. Toque em Copiar Resumo.

3. Informe usuário, horário, ação realizada e mensagem exibida.

4. Se for impressão, informe tipo de conexão e modelo aproximado da impressora.

5. Se for backup ou atualização, informe host FTP e se o teste de conexão funciona.

6. Se for WhatsBot, confirme permissões de notificações e acessibilidade.
