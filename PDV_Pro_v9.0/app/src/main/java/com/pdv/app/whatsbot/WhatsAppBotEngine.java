package com.pdv.app.whatsbot;

import android.content.Context;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.CupomGenerator;
import com.pdv.app.utils.FormatUtils;
import com.pdv.app.utils.PrinterManager;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de processamento de mensagens do Bot WhatsApp.
 * Gerencia sessoes de conversa, interpreta comandos e gera respostas
 * inteligentes baseadas no banco de dados do PDV.
 *
 * OTIMIZADO v4.0: Vinculacao de pedido ao celular do cliente,
 * solicitacao de nome e endereco com persistencia automatica.
 *
 * v5.0.0 - IA INTEGRADA:
 * - Interpretacao de linguagem natural via GPT/OpenAI
 * - Classificacao inteligente de intencoes do cliente
 * - Respostas humanizadas e contextuais
 * - Sugestao de produtos baseada em historico
 * - Deteccao de frustracao do cliente
 * - Busca inteligente de produtos por texto livre
 *
 * v6.1.0 - SELECAO OBRIGATORIA DE BAIRRO:
 * - Cliente DEVE escolher um bairro entre os cadastrados
 * - Taxa de entrega do bairro e somada ao total do pedido
 * - Sem bairros cadastrados, pedido nao pode ser finalizado
 */
public class WhatsAppBotEngine {
    private static final String TAG = "WhatsAppBotEngine";
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000; // 30 minutos

    private Context context;
    private WhatsAppBotConfig config;
    private WhatsAppBotLogger logger;
    private DatabaseHelper dbHelper;
    private WhatsAppAIHelper aiHelper;

    // Sessoes de conversa ativas (contato -> sessao)
    private ConcurrentHashMap<String, ConversationSession> sessions;

    /**
     * Representa uma sessao de conversa com um contato.
     */
    public static class ConversationSession {
        public String contato;
        public String estado;
        public long ultimaInteracao;
        public List<PedidoItem> itensPedido;
        public String clienteNome;
        public int clienteId;
        public String buscaAtual;
        public int paginaCatalogo;
        public List<int[]> categoriasCache;
        public List<int[]> produtosCache;
        public int categoriaSelecionadaId;
        public int produtoSelecionadoId;
        public String produtoSelecionadoDesc;
        public double produtoSelecionadoPreco;

        // v4.0 - Dados do cliente para pedido
        public String celularWhatsApp;     // Numero do celular extraido do contato WhatsApp
        public String enderecoCompleto;    // Endereco completo digitado pelo cliente
        public String enderecoSalvo;       // Endereco salvo no banco (persistencia)
        public String nomeSalvo;           // Nome salvo no banco (persistencia)
        public boolean clienteExistente;   // Se o cliente ja existe no banco
        public int clienteIdEncontrado;    // ID do cliente encontrado no banco

        // v4.5 - Taxa de entrega por bairro
        public int bairroEntregaId;        // ID do bairro selecionado
        public String bairroEntregaNome;   // Nome do bairro selecionado
        public double taxaEntrega;         // Valor da taxa de entrega
        public String bairroSalvo;         // Bairro salvo do ultimo pedido (memoria)
        public int bairroSalvoId;          // ID do bairro salvo
        public double taxaSalva;           // Taxa salva do ultimo pedido
        public List<int[]> bairrosCache;   // Cache de bairros disponiveis [id]
        public List<String> bairrosNomes;  // Cache de nomes dos bairros
        public List<Double> bairrosTaxas;  // Cache de taxas dos bairros

        // v6.3.0 - Adicionais
        public double quantidadePendente;  // Quantidade digitada, aguardando selecao de adicionais
        public List<int[]> adicionaisCache;   // Cache de adicionais disponiveis para o tipo do produto [id]
        public List<String> adicionaisNomes;  // Cache de nomes dos adicionais
        public List<Double> adicionaisPrecos; // Cache de precos dos adicionais
        public List<AdicionalSelecionado> adicionaisSelecionados; // Adicionais escolhidos para o item atual

        public ConversationSession(String contato) {
            this.contato = contato;
            this.estado = "NOVO";
            this.ultimaInteracao = System.currentTimeMillis();
            this.itensPedido = new ArrayList<>();
            this.paginaCatalogo = 0;
            this.categoriasCache = new ArrayList<>();
            this.produtosCache = new ArrayList<>();
            this.categoriaSelecionadaId = 0;
            this.produtoSelecionadoId = 0;
            this.clienteExistente = false;
            this.clienteIdEncontrado = 0;
            this.bairroEntregaId = 0;
            this.taxaEntrega = 0;
            this.bairrosCache = new ArrayList<>();
            this.bairrosNomes = new ArrayList<>();
            this.bairrosTaxas = new ArrayList<>();
            this.adicionaisCache = new ArrayList<>();
            this.adicionaisNomes = new ArrayList<>();
            this.adicionaisPrecos = new ArrayList<>();
            this.adicionaisSelecionados = new ArrayList<>();
        }

        public boolean isExpirada() {
            return System.currentTimeMillis() - ultimaInteracao > SESSION_TIMEOUT;
        }

        public void atualizarInteracao() {
            this.ultimaInteracao = System.currentTimeMillis();
        }
    }

    /**
     * Adicional selecionado pelo cliente.
     */
    public static class AdicionalSelecionado {
        public int adicionalId;
        public String descricao;
        public double preco;

        public AdicionalSelecionado(int adicionalId, String descricao, double preco) {
            this.adicionalId = adicionalId;
            this.descricao = descricao;
            this.preco = preco;
        }
    }

    /**
     * Item de um pedido em andamento.
     */
    public static class PedidoItem {
        public int produtoId;
        public String descricao;
        public double quantidade;
        public double precoUnitario;
        public double total;
        public List<AdicionalSelecionado> adicionais;

        public PedidoItem(int produtoId, String descricao, double quantidade, double precoUnitario) {
            this.produtoId = produtoId;
            this.descricao = descricao;
            this.quantidade = quantidade;
            this.precoUnitario = precoUnitario;
            this.total = quantidade * precoUnitario;
            this.adicionais = new ArrayList<>();
        }

        public double getTotalComAdicionais() {
            double totalAdicionais = 0;
            for (AdicionalSelecionado ad : adicionais) totalAdicionais += ad.preco;
            return total + (totalAdicionais * quantidade);
        }
    }

    public WhatsAppBotEngine(Context context) {
        this.context = context;
        this.config = new WhatsAppBotConfig(context);
        this.logger = new WhatsAppBotLogger(context);
        this.dbHelper = DatabaseHelper.getInstance(context);
        this.aiHelper = new WhatsAppAIHelper(context);
        this.sessions = new ConcurrentHashMap<>();
    }

    /**
     * Processa uma mensagem recebida e retorna a resposta do bot.
     */
    public String processarMensagem(String contato, String mensagem) {
        try {
            logger.logMsgRecebida(contato, mensagem);

            limparSessoesExpiradas();

            if (!isDentroHorario() && !config.isAtenderForaHorario()) {
                String resp = config.processarTemplate(config.getMsgAusencia());
                logger.logMsgEnviada(contato, resp);
                return resp;
            }

            ConversationSession session = sessions.get(contato);
            if (session == null || session.isExpirada()) {
                session = new ConversationSession(contato);
                session.celularWhatsApp = extrairCelularDoContato(contato);
                sessions.put(contato, session);
            }
            session.atualizarInteracao();

            String msg = mensagem.trim().toLowerCase();
            String resposta;

            switch (session.estado) {
                case "NOVO":
                    resposta = processarNovo(session, msg);
                    break;
                case "MENU":
                    resposta = processarMenu(session, msg);
                    break;
                case "CATALOGO":
                    resposta = processarCatalogo(session, msg);
                    break;
                case "PRECO":
                    resposta = processarConsultaPreco(session, msg);
                    break;

                case "PEDIDO_CATEGORIAS":
                    resposta = processarPedidoCategorias(session, msg);
                    break;
                case "PEDIDO_PRODUTOS":
                    resposta = processarPedidoProdutos(session, msg);
                    break;
                case "PEDIDO_QUANTIDADE":
                    resposta = processarPedidoQuantidade(session, msg);
                    break;
                case "PEDIDO_ADICIONAIS":
                    resposta = processarPedidoAdicionais(session, msg);
                    break;
                case "PEDIDO_CONTINUAR":
                    resposta = processarPedidoContinuar(session, msg);
                    break;
                case "PEDIDO_NOME":
                    resposta = processarPedidoNome(session, mensagem.trim());
                    break;
                case "PEDIDO_ENDERECO":
                    resposta = processarPedidoEndereco(session, mensagem.trim());
                    break;
                case "PEDIDO_CONFIRMA_ENDERECO":
                    resposta = processarPedidoConfirmaEndereco(session, msg);
                    break;
                case "PEDIDO_BAIRRO":
                    resposta = processarPedidoBairro(session, msg);
                    break;
                case "PEDIDO_CONFIRMA_BAIRRO":
                    resposta = processarPedidoConfirmaBairro(session, msg);
                    break;
                case "PEDIDO_CONFIRMA":
                    resposta = processarPedidoConfirma(session, msg);
                    break;
                case "CONSULTA_PEDIDO":
                    resposta = processarConsultaPedido(session, msg);
                    break;
                case "RASTREAR_ENTREGADOR":
                    resposta = processarRastrearEntregador(session, msg);
                    break;
                case "ATENDENTE":
                    resposta = processarAtendente(session, msg);
                    break;
                case "ENCERRADO":
                    session.estado = "NOVO";
                    resposta = processarNovo(session, msg);
                    break;
                default:
                    session.estado = "MENU";
                    resposta = getMenuPrincipal();
                    break;
            }

            if (resposta == null) {
                Log.d(TAG, "Resposta null para " + contato + ", mensagem ignorada (anti-loop)");
                return null;
            }

            logger.logMsgEnviada(contato, resposta);
            return resposta;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao processar mensagem", e);
            logger.logErro("Erro ao processar mensagem de " + contato, e);
            return "Desculpe, ocorreu um erro. Digite *0* para o menu principal.";
        }
    }

    // ===================== PROCESSADORES DE ESTADO =====================

    private String processarNovo(ConversationSession session, String msg) {
        if (pareceRespostaDoBot(msg)) {
            return null;
        }
        session.estado = "MENU";
        String boasVindas = config.processarTemplate(config.getMsgBoasVindas());

        // v5.0.0 - Adicionar sugestoes de produtos se IA habilitada
        if (config.isIAEnabled() && config.isIASugestoesProdutos()
                && session.celularWhatsApp != null && !session.celularWhatsApp.isEmpty()) {
            try {
                String sugestoes = aiHelper.gerarSugestoesProdutos(session.celularWhatsApp);
                if (sugestoes != null && !sugestoes.isEmpty()) {
                    boasVindas += "\n\n\uD83C\uDF1F *Sugestoes para voce:*\n" + sugestoes;
                }
            } catch (Exception e) {
                Log.d(TAG, "Erro ao gerar sugestoes IA: " + e.getMessage());
            }
        }

        return boasVindas;
    }

    private String processarMenu(ConversationSession session, String msg) {
        switch (msg) {
            case "1":
                if (config.isEnviarCatalogo()) {
                    session.estado = "CATALOGO";
                    session.paginaCatalogo = 0;
                    return gerarCatalogo(0);
                }
                return "Catalogo desativado no momento.\n\n" + getMenuCompacto();

            case "2":
                if (config.isConsultarPreco()) {
                    session.estado = "PRECO";
                    return "\uD83D\uDCB0 *Consulta de Preco*\n\n"
                            + "Digite o nome do produto:\n\n"
                            + "*0* - Voltar ao menu";
                }
                return "Consulta de preco desativada no momento.\n\n" + getMenuCompacto();

            case "3":
                if (config.isAceitarPedidos()) {
                    session.itensPedido.clear();
                    return listarCategoriasParaPedido(session);
                }
                return "Pedidos desativados no momento.\n\n" + getMenuCompacto();

            case "4":
                session.estado = "CONSULTA_PEDIDO";
                return "\uD83D\uDCCB *Consultar Pedido*\n\n"
                        + "Digite o numero do pedido:\n\n"
                        + "*0* - Voltar ao menu";

            case "5":
                return gerarInfoContato(session);

            case "6":
                session.estado = "ATENDENTE";
                return "\uD83D\uDC64 *Atendimento Humano*\n\n"
                        + "Descreva o motivo do seu contato.\n"
                        + "Um atendente respondera em breve.\n\n"
                        + "*0* - Voltar ao menu";

            case "7":
                session.estado = "RASTREAR_ENTREGADOR";
                return "\uD83D\uDCCD *Rastrear Entregador*\n\n"
                        + "Digite o numero do pedido\n"
                        + "ou *99* para ver entregadores ativos.\n\n"
                        + "*0* - Voltar ao menu";

            case "0":
                session.estado = "ENCERRADO";
                return config.processarTemplate(config.getMsgEncerramento());

            default:
                if (pareceRespostaDoBot(msg)) {
                    return null;
                }
                if (msg.matches("^(oi|ola|olá|bom dia|boa tarde|boa noite|hey|hi|hello|e ai|eai|menu)$")) {
                    return getMenuPrincipal();
                }
                return interpretarIntencao(session, msg);
        }
    }

    private String processarCatalogo(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            session.estado = "MENU";
            return getMenuPrincipal();
        }
        if (msg.equals("9")) {
            session.paginaCatalogo++;
            return gerarCatalogo(session.paginaCatalogo);
        }
        if (msg.equals("8") && session.paginaCatalogo > 0) {
            session.paginaCatalogo--;
            return gerarCatalogo(session.paginaCatalogo);
        }
        try {
            int prodId = Integer.parseInt(msg);
            return gerarDetalheProduto(prodId);
        } catch (NumberFormatException e) {
            return buscarProdutosCatalogo(msg);
        }
    }

    private String processarConsultaPreco(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            session.estado = "MENU";
            return getMenuPrincipal();
        }
        return buscarPreco(msg);
    }



    // ===================== FLUXO DE PEDIDO POR CATEGORIAS =====================

    private String listarCategoriasParaPedido(ConversationSession session) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT tp.id, tp.descricao FROM tipos_produto tp "
                            + "INNER JOIN produtos p ON p.tipo_produto_id = tp.id "
                            + "WHERE tp.ativo = 1 AND p.ativo = 1 AND p.preco_venda > 0 "
                            + "ORDER BY tp.descricao ASC");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDED2 *Fazer Pedido*\n");
            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
            sb.append("Escolha a categoria:\n\n");

            session.categoriasCache.clear();
            int num = 0;
            while (rs.next()) {
                num++;
                int catId = rs.getInt("id");
                String catDesc = rs.getString("descricao");
                session.categoriasCache.add(new int[]{catId, num});
                sb.append("*").append(num).append("* - \uD83D\uDCC2 ").append(catDesc).append("\n");
            }
            rs.close();
            ps.close();

            if (num == 0) {
                session.estado = "MENU";
                return "Nenhuma categoria disponivel.\n\n" + getMenuCompacto();
            }

            session.estado = "PEDIDO_CATEGORIAS";
            sb.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            sb.append("Digite o *numero* da categoria\n");
            sb.append("*0* - Voltar ao menu");
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar categorias para pedido", e);
            return "Erro ao carregar categorias. Tente novamente.";
        }
    }

    private String processarPedidoCategorias(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            session.estado = "MENU";
            session.itensPedido.clear();
            return getMenuPrincipal();
        }
        try {
            int escolha = Integer.parseInt(msg);
            int catId = -1;
            for (int[] cat : session.categoriasCache) {
                if (cat[1] == escolha) {
                    catId = cat[0];
                    break;
                }
            }
            if (catId == -1) {
                return "\u26A0\uFE0F Numero invalido. Digite de *1* a *" + session.categoriasCache.size() + "*\n*0* - Voltar ao menu";
            }
            session.categoriaSelecionadaId = catId;
            return listarProdutosDaCategoria(session, catId);
        } catch (NumberFormatException e) {
            return "\u26A0\uFE0F Digite apenas o *numero* da categoria.\n*0* - Voltar ao menu";
        }
    }

    private String listarProdutosDaCategoria(ConversationSession session, int categoriaId) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement pscat = conn.prepareStatement("SELECT descricao FROM tipos_produto WHERE id = ?");
            pscat.setInt(1, categoriaId);
            ResultSet rscat = pscat.executeQuery();
            String nomeCategoria = "";
            if (rscat.next()) nomeCategoria = rscat.getString("descricao");
            rscat.close();
            pscat.close();

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, descricao, preco_venda, unidade FROM produtos "
                            + "WHERE ativo = 1 AND preco_venda > 0 AND tipo_produto_id = ? "
                            + "ORDER BY descricao ASC");
            ps.setInt(1, categoriaId);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDCC2 *").append(nomeCategoria).append("*\n");
            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            session.produtosCache.clear();
            int num = 0;
            while (rs.next()) {
                num++;
                int prodId = rs.getInt("id");
                String desc = rs.getString("descricao");
                double preco = rs.getDouble("preco_venda");
                String unidade = rs.getString("unidade");

                session.produtosCache.add(new int[]{prodId, num});
                sb.append("*").append(num).append("* - ").append(desc);
                sb.append(" | R$ ").append(FormatUtils.formatMoney(preco));
                sb.append("/").append(unidade != null ? unidade : "UN");
                sb.append("\n");
            }
            rs.close();
            ps.close();

            if (num == 0) {
                return "Nenhum produto nesta categoria.\n\n*0* - Voltar as categorias";
            }

            session.estado = "PEDIDO_PRODUTOS";
            sb.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            sb.append("Digite o *numero* do produto\n");
            sb.append("*0* - Voltar as categorias");
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar produtos da categoria", e);
            return "Erro ao carregar produtos. Tente novamente.";
        }
    }

    private String processarPedidoProdutos(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            return listarCategoriasParaPedido(session);
        }
        try {
            int escolha = Integer.parseInt(msg);
            int prodId = -1;
            for (int[] prod : session.produtosCache) {
                if (prod[1] == escolha) {
                    prodId = prod[0];
                    break;
                }
            }
            if (prodId == -1) {
                return "\u26A0\uFE0F Numero invalido. Digite de *1* a *" + session.produtosCache.size() + "*\n*0* - Voltar as categorias";
            }
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, descricao, preco_venda FROM produtos WHERE id = ? AND ativo = 1");
            ps.setInt(1, prodId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                session.produtoSelecionadoId = rs.getInt("id");
                session.produtoSelecionadoDesc = rs.getString("descricao");
                session.produtoSelecionadoPreco = rs.getDouble("preco_venda");
                rs.close();
                ps.close();

                session.estado = "PEDIDO_QUANTIDADE";
                StringBuilder sb = new StringBuilder();
                sb.append("\uD83D\uDCE6 *").append(session.produtoSelecionadoDesc).append("*\n");
                sb.append("\uD83D\uDCB0 Preco: *R$ ").append(FormatUtils.formatMoney(session.produtoSelecionadoPreco)).append("*\n");
                sb.append("\nDigite a *quantidade*:\n");
                sb.append("_(Ex: 1, 2, 0.5)_\n\n");
                sb.append("*0* - Voltar aos produtos");
                return sb.toString();
            }
            rs.close();
            ps.close();
            return "Produto nao encontrado. Tente novamente.";
        } catch (NumberFormatException e) {
            return "\u26A0\uFE0F Digite apenas o *numero* do produto.\n*0* - Voltar as categorias";
        } catch (Exception e) {
            Log.e(TAG, "Erro ao selecionar produto", e);
            return "Erro ao selecionar produto. Tente novamente.";
        }
    }

    private String processarPedidoQuantidade(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            return listarProdutosDaCategoria(session, session.categoriaSelecionadaId);
        }
        try {
            double qtd = Double.parseDouble(msg.replace(",", "."));
            if (qtd <= 0) {
                return "\u26A0\uFE0F Quantidade invalida. Digite um valor maior que zero.\n*0* - Voltar";
            }
            session.quantidadePendente = qtd;
            // v6.3.0 - Verificar se o tipo do produto tem adicionais vinculados
            return verificarAdicionaisDoProduto(session, session.produtoSelecionadoId, qtd);
        } catch (NumberFormatException e) {
            return "\u26A0\uFE0F Digite apenas numeros para a quantidade.\n_(Ex: 1, 2, 0.5)_\n\n*0* - Voltar";
        }
    }

    /**
     * v6.3.0 - Verifica se o produto tem adicionais vinculados ao seu tipo.
     * Se tiver, exibe a lista para o cliente escolher. Se nao tiver, adiciona direto ao carrinho.
     */
    private String verificarAdicionaisDoProduto(ConversationSession session, int produtoId, double quantidade) {
        try {
            Connection conn = dbHelper.getConnection();
            // Buscar tipo_produto_id do produto
            PreparedStatement ps = conn.prepareStatement(
                "SELECT tipo_produto_id FROM produtos WHERE id = ?");
            ps.setInt(1, produtoId);
            ResultSet rs = ps.executeQuery();
            int tipoId = 0;
            if (rs.next()) {
                tipoId = rs.getInt("tipo_produto_id");
            }
            rs.close();
            ps.close();

            if (tipoId > 0) {
                // Buscar adicionais vinculados ao tipo
                ps = conn.prepareStatement(
                    "SELECT a.id, a.descricao, a.preco FROM tipo_produto_adicionais tpa " +
                    "INNER JOIN adicionais a ON tpa.adicional_id = a.id " +
                    "WHERE tpa.tipo_produto_id = ? AND a.ativo = 1 ORDER BY a.descricao");
                ps.setInt(1, tipoId);
                rs = ps.executeQuery();

                session.adicionaisCache.clear();
                session.adicionaisNomes.clear();
                session.adicionaisPrecos.clear();
                session.adicionaisSelecionados.clear();

                while (rs.next()) {
                    session.adicionaisCache.add(new int[]{rs.getInt("id")});
                    session.adicionaisNomes.add(rs.getString("descricao"));
                    session.adicionaisPrecos.add(rs.getDouble("preco"));
                }
                rs.close();
                ps.close();

                if (!session.adicionaisCache.isEmpty()) {
                    // Tem adicionais - exibir lista
                    session.estado = "PEDIDO_ADICIONAIS";
                    return montarMenuAdicionais(session);
                }
            }

            // Sem adicionais - adicionar direto ao carrinho
            return adicionarItemPedido(session, produtoId, quantidade);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar adicionais", e);
            return adicionarItemPedido(session, produtoId, quantidade);
        }
    }

    /**
     * v6.3.0 - Monta o menu de adicionais para o cliente escolher.
     */
    private String montarMenuAdicionais(ConversationSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u2795 *ADICIONAIS para " + session.produtoSelecionadoDesc + "*\n");
        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

        for (int i = 0; i < session.adicionaisNomes.size(); i++) {
            String nome = session.adicionaisNomes.get(i);
            double preco = session.adicionaisPrecos.get(i);
            // Verificar se ja foi selecionado
            boolean selecionado = false;
            for (AdicionalSelecionado as : session.adicionaisSelecionados) {
                if (as.adicionalId == session.adicionaisCache.get(i)[0]) {
                    selecionado = true;
                    break;
                }
            }
            sb.append("*").append(i + 1).append("* - ");
            if (selecionado) sb.append("\u2705 ");
            sb.append(nome);
            if (preco > 0) {
                sb.append(" _(+R$ ").append(FormatUtils.formatMoney(preco)).append(")_");
            } else {
                sb.append(" _(Gratis)_");
            }
            sb.append("\n");
        }

        sb.append("\n");
        if (!session.adicionaisSelecionados.isEmpty()) {
            sb.append("\u2705 *Selecionados:* ").append(session.adicionaisSelecionados.size()).append("\n");
        }
        sb.append("\n*0* - \u2705 Continuar sem mais adicionais\n");
        sb.append("*00* - \u274C Nenhum adicional");
        return sb.toString();
    }

    /**
     * v6.3.0 - Processa a escolha de adicionais pelo cliente.
     */
    private String processarPedidoAdicionais(ConversationSession session, String msg) {
        if (msg.equals("00")) {
            // Nenhum adicional - limpar e adicionar ao carrinho
            session.adicionaisSelecionados.clear();
            return adicionarItemPedidoComAdicionais(session, session.produtoSelecionadoId, session.quantidadePendente);
        }
        if (msg.equals("0")) {
            // Continuar com os adicionais ja selecionados
            return adicionarItemPedidoComAdicionais(session, session.produtoSelecionadoId, session.quantidadePendente);
        }
        try {
            int escolha = Integer.parseInt(msg);
            if (escolha < 1 || escolha > session.adicionaisCache.size()) {
                return "\u26A0\uFE0F Numero invalido. Digite de *1* a *" + session.adicionaisCache.size() + "*\n*0* - Continuar\n*00* - Nenhum adicional";
            }

            int adId = session.adicionaisCache.get(escolha - 1)[0];
            String adNome = session.adicionaisNomes.get(escolha - 1);
            double adPreco = session.adicionaisPrecos.get(escolha - 1);

            // Toggle: se ja selecionado, remove; senao, adiciona
            boolean encontrado = false;
            for (int i = 0; i < session.adicionaisSelecionados.size(); i++) {
                if (session.adicionaisSelecionados.get(i).adicionalId == adId) {
                    session.adicionaisSelecionados.remove(i);
                    encontrado = true;
                    break;
                }
            }
            if (!encontrado) {
                session.adicionaisSelecionados.add(new AdicionalSelecionado(adId, adNome, adPreco));
            }

            return montarMenuAdicionais(session);
        } catch (NumberFormatException e) {
            return "\u26A0\uFE0F Digite o *numero* do adicional.\n*0* - Continuar\n*00* - Nenhum adicional";
        }
    }

    /**
     * v6.3.0 - Adiciona item ao pedido incluindo adicionais selecionados.
     */
    private String adicionarItemPedidoComAdicionais(ConversationSession session, int produtoId, double quantidade) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, descricao, preco_venda FROM produtos WHERE id = ? AND ativo = 1");
            ps.setInt(1, produtoId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String desc = rs.getString("descricao");
                double preco = rs.getDouble("preco_venda");

                rs.close();
                ps.close();

                PedidoItem item = new PedidoItem(produtoId, desc, quantidade, preco);
                // Copiar adicionais selecionados para o item
                for (AdicionalSelecionado as : session.adicionaisSelecionados) {
                    item.adicionais.add(new AdicionalSelecionado(as.adicionalId, as.descricao, as.preco));
                }
                session.itensPedido.add(item);
                session.estado = "PEDIDO_CONTINUAR";

                double totalCarrinho = 0;
                for (PedidoItem pi : session.itensPedido) totalCarrinho += pi.getTotalComAdicionais();

                StringBuilder resposta = new StringBuilder();
                resposta.append("\u2705 *Adicionado ao carrinho:*\n\n");
                resposta.append("\u2022 ").append(desc).append("\n");
                resposta.append("  Qtd: ").append(FormatUtils.formatQuantidade(quantidade))
                        .append(" x R$ ").append(FormatUtils.formatMoney(preco)).append("\n");

                if (!item.adicionais.isEmpty()) {
                    resposta.append("  \u2795 Adicionais:\n");
                    for (AdicionalSelecionado ad : item.adicionais) {
                        resposta.append("    - ").append(ad.descricao);
                        if (ad.preco > 0) {
                            resposta.append(" (+R$ ").append(FormatUtils.formatMoney(ad.preco)).append(")");
                        }
                        resposta.append("\n");
                    }
                }

                resposta.append("  Subtotal: *R$ ").append(FormatUtils.formatMoney(item.getTotalComAdicionais())).append("*\n\n");
                resposta.append("\uD83D\uDED2 Itens: *").append(session.itensPedido.size()).append("*\n");
                resposta.append("\uD83D\uDCB0 Total: *R$ ").append(FormatUtils.formatMoney(totalCarrinho)).append("*\n\n");
                resposta.append("*1* - \uD83D\uDED2 Continuar comprando\n");
                resposta.append("*2* - \u2705 Finalizar pedido\n");
                resposta.append("*3* - \u274C Cancelar pedido");
                return resposta.toString();
            }

            rs.close();
            ps.close();
            return "Produto nao encontrado.";

        } catch (Exception e) {
            Log.e(TAG, "Erro ao adicionar item com adicionais", e);
            return "Erro ao adicionar produto. Tente novamente.";
        }
    }

    private String processarPedidoContinuar(ConversationSession session, String msg) {
        switch (msg) {
            case "1":
                return listarCategoriasParaPedido(session);
            case "2":
                if (session.itensPedido.isEmpty()) {
                    return "Carrinho vazio! Adicione pelo menos um produto.\n\n*1* - Escolher produtos";
                }
                // v4.0 - Iniciar fluxo de coleta de dados do cliente
                return iniciarColetaDadosCliente(session);
            case "3":
                session.itensPedido.clear();
                session.estado = "MENU";
                return "\u274C Pedido cancelado.\n\n" + getMenuPrincipal();
            default:
                return "\u26A0\uFE0F Opcao invalida.\n\n*1* - Continuar comprando\n*2* - Finalizar pedido\n*3* - Cancelar pedido";
        }
    }

    // ===================== v4.0 - FLUXO DE COLETA DE DADOS DO CLIENTE =====================

    /**
     * Inicia o fluxo de coleta de dados do cliente.
     * Verifica se o cliente ja existe no banco pelo celular do WhatsApp.
     * Se existir, oferece usar os dados salvos.
     */
    private String iniciarColetaDadosCliente(ConversationSession session) {
        try {
            // Tentar encontrar cliente pelo celular do WhatsApp
            String celular = session.celularWhatsApp;
            if (celular != null && !celular.isEmpty()) {
                ClienteDados dados = buscarClientePorCelular(celular);
                if (dados != null) {
                    session.clienteExistente = true;
                    session.clienteIdEncontrado = dados.id;
                    session.nomeSalvo = dados.nome;
                    session.enderecoSalvo = dados.enderecoCompleto;

                    // Cliente encontrado - perguntar se quer usar mesmo endereco
                    session.estado = "PEDIDO_CONFIRMA_ENDERECO";
                    StringBuilder sb = new StringBuilder();
                    sb.append("\uD83D\uDC64 *Dados encontrados!*\n");
                    sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
                    sb.append("\uD83D\uDC64 Nome: *").append(dados.nome).append("*\n");
                    if (dados.enderecoCompleto != null && !dados.enderecoCompleto.isEmpty()) {
                        sb.append("\uD83D\uDCCD Endereco: *").append(dados.enderecoCompleto).append("*\n");
                    }
                    sb.append("\uD83D\uDCF1 Celular: *").append(celular).append("*\n\n");
                    sb.append("Deseja enviar para o *mesmo endereco*?\n\n");
                    sb.append("*1* - \u2705 Sim, mesmo endereco\n");
                    sb.append("*2* - \u270F\uFE0F Nao, informar novo endereco\n");
                    sb.append("*3* - \u274C Cancelar pedido");
                    return sb.toString();
                }
            }

            // Cliente nao encontrado - solicitar nome
            session.clienteExistente = false;
            session.estado = "PEDIDO_NOME";
            return "\uD83D\uDC64 *Dados para entrega*\n"
                    + "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n"
                    + "Por favor, digite seu *nome completo*:\n\n"
                    + "*0* - Cancelar pedido";

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar coleta de dados do cliente", e);
            // Em caso de erro, prosseguir sem dados
            session.estado = "PEDIDO_NOME";
            return "\uD83D\uDC64 *Dados para entrega*\n\n"
                    + "Por favor, digite seu *nome completo*:\n\n"
                    + "*0* - Cancelar pedido";
        }
    }

    /**
     * Processa o nome digitado pelo cliente.
     */
    private String processarPedidoNome(ConversationSession session, String nomeDigitado) {
        if (nomeDigitado.equals("0")) {
            session.itensPedido.clear();
            session.estado = "MENU";
            return "\u274C Pedido cancelado.\n\n" + getMenuPrincipal();
        }

        if (nomeDigitado.length() < 3) {
            return "\u26A0\uFE0F Nome muito curto. Digite seu *nome completo*:\n\n*0* - Cancelar pedido";
        }

        session.clienteNome = nomeDigitado;
        session.estado = "PEDIDO_ENDERECO";

        return "\u2705 Nome: *" + nomeDigitado + "*\n\n"
                + "\uD83D\uDCCD Agora digite seu *endereco completo* para entrega:\n"
                + "_(Rua, numero)_\n\n"
                + "*0* - Cancelar pedido";
    }

    /**
     * Processa o endereco digitado pelo cliente.
     */
    private String processarPedidoEndereco(ConversationSession session, String enderecoDigitado) {
        if (enderecoDigitado.equals("0")) {
            session.itensPedido.clear();
            session.estado = "MENU";
            return "\u274C Pedido cancelado.\n\n" + getMenuPrincipal();
        }

        if (enderecoDigitado.length() < 5) {
            return "\u26A0\uFE0F Endereco muito curto. Digite o *endereco completo*:\n"
                    + "_(Rua, numero)_\n\n*0* - Cancelar pedido";
        }

        session.enderecoCompleto = enderecoDigitado;

        // v4.5 - Ir para selecao obrigatoria de bairro (taxa entrega)
        return iniciarSelecaoBairro(session);
    }

    /**
     * Processa a confirmacao de endereco para cliente existente.
     */
    private String processarPedidoConfirmaEndereco(ConversationSession session, String msg) {
        switch (msg) {
            case "1":
                // Usar mesmo endereco salvo
                session.clienteNome = session.nomeSalvo;
                session.enderecoCompleto = session.enderecoSalvo;
                session.clienteId = session.clienteIdEncontrado;
                // v4.5 - Ir para selecao de bairro
                return iniciarSelecaoBairro(session);

            case "2":
                // Informar novo endereco
                session.estado = "PEDIDO_NOME";
                return "\uD83D\uDC64 *Novos dados para entrega*\n"
                        + "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n"
                        + "Por favor, digite seu *nome completo*:\n\n"
                        + "*0* - Cancelar pedido";

            case "3":
                session.itensPedido.clear();
                session.estado = "MENU";
                return "\u274C Pedido cancelado.\n\n" + getMenuPrincipal();

            default:
                return "\u26A0\uFE0F Opcao invalida.\n\n"
                        + "*1* - \u2705 Sim, mesmo endereco\n"
                        + "*2* - \u270F\uFE0F Nao, informar novo endereco\n"
                        + "*3* - \u274C Cancelar pedido";
        }
    }

    // ===================== v4.5 - SELECAO OBRIGATORIA DE BAIRRO =====================

    /**
     * Inicia a selecao de bairro para entrega.
     * Verifica se o cliente ja tem um bairro salvo (memoria).
     */
    private String iniciarSelecaoBairro(ConversationSession session) {
        try {
            Connection conn = dbHelper.getConnection();

            // Carregar bairros disponiveis
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM taxa_entrega_bairro WHERE ativo = 1 ORDER BY bairro");
            session.bairrosCache.clear();
            session.bairrosNomes.clear();
            session.bairrosTaxas.clear();
            while (rs.next()) {
                session.bairrosCache.add(new int[]{rs.getInt("id")});
                session.bairrosNomes.add(rs.getString("bairro"));
                session.bairrosTaxas.add(rs.getDouble("taxa"));
            }
            rs.close();
            stmt.close();

            if (session.bairrosNomes.isEmpty()) {
                // v6.1.0 - Sem bairros cadastrados - NAO permite finalizar pedido
                // O lojista precisa cadastrar pelo menos um bairro
                session.estado = "PEDIDO_CONTINUAR";
                return "\u26A0\uFE0F *Nenhum bairro cadastrado!*\n"
                        + "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n"
                        + "Nao e possivel finalizar o pedido pois nenhum bairro de entrega foi cadastrado pelo estabelecimento.\n\n"
                        + "Por favor, entre em contato com o estabelecimento para que cadastre os bairros de entrega.\n\n"
                        + "*1* - \uD83D\uDED2 Continuar comprando\n"
                        + "*2* - \u2705 Tentar finalizar novamente\n"
                        + "*3* - \u274C Cancelar pedido";
            }

            // Verificar se tem bairro salvo para este telefone
            String celular = session.celularWhatsApp;
            if (celular != null && !celular.isEmpty()) {
                try {
                    PreparedStatement ps = conn.prepareStatement(
                        "SELECT cb.bairro_id, tb.bairro, tb.taxa FROM cliente_bairro_whatsapp cb "
                        + "INNER JOIN taxa_entrega_bairro tb ON cb.bairro_id = tb.id "
                        + "WHERE cb.celular_whatsapp LIKE ? AND tb.ativo = 1 "
                        + "ORDER BY cb.data_atualizacao DESC LIMIT 1");
                    String celLimpo = celular.replaceAll("[^0-9]", "");
                    ps.setString(1, "%" + celLimpo + "%");
                    ResultSet rsBairro = ps.executeQuery();
                    if (rsBairro.next()) {
                        session.bairroSalvoId = rsBairro.getInt("bairro_id");
                        session.bairroSalvo = rsBairro.getString("bairro");
                        session.taxaSalva = rsBairro.getDouble("taxa");
                    }
                    rsBairro.close();
                    ps.close();
                } catch (Exception ignored) {}
            }

            // Se tem bairro salvo, perguntar se quer usar o mesmo
            if (session.bairroSalvo != null && !session.bairroSalvo.isEmpty()) {
                session.estado = "PEDIDO_CONFIRMA_BAIRRO";
                StringBuilder sb = new StringBuilder();
                sb.append("\uD83D\uDE9A *Bairro de Entrega*\n");
                sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
                sb.append("Seu ultimo pedido foi para:\n");
                sb.append("\uD83D\uDCCD *").append(session.bairroSalvo).append("*\n\n");
                sb.append("Deseja entregar no *mesmo bairro*?\n\n");
                sb.append("*1* - \u2705 Sim, mesmo bairro\n");
                sb.append("*2* - \u270F\uFE0F Nao, escolher outro bairro\n");
                sb.append("*3* - \u274C Cancelar pedido");
                return sb.toString();
            }

            // Sem bairro salvo - listar bairros
            return listarBairrosParaSelecao(session);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar selecao de bairro", e);
            // v6.1.0 - Em caso de erro, informar o cliente e nao prosseguir sem bairro
            session.estado = "PEDIDO_CONTINUAR";
            return "\u26A0\uFE0F *Erro ao carregar bairros de entrega.*\n\n"
                    + "Nao foi possivel carregar os bairros disponiveis.\n"
                    + "Tente novamente em instantes.\n\n"
                    + "*1* - \uD83D\uDED2 Continuar comprando\n"
                    + "*2* - \u2705 Tentar finalizar novamente\n"
                    + "*3* - \u274C Cancelar pedido";
        }
    }

    /**
     * Lista os bairros disponiveis para selecao.
     * v6.1.0 - Selecao OBRIGATORIA com taxa de entrega.
     */
    private String listarBairrosParaSelecao(ConversationSession session) {
        session.estado = "PEDIDO_BAIRRO";
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDE9A *Selecione o Bairro de Entrega*\n");
        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
        sb.append("\u26A0\uFE0F _A escolha do bairro e obrigatoria._\n");
        sb.append("_O valor da taxa sera somado ao total do pedido._\n\n");

        for (int i = 0; i < session.bairrosNomes.size(); i++) {
            sb.append("*").append(i + 1).append("* - ");
            sb.append(session.bairrosNomes.get(i)).append("\n");
        }

        sb.append("\n*0* - \u274C Cancelar pedido");
        return sb.toString();
    }

    /**
     * Processa a selecao de bairro pelo numero.
     */
    private String processarPedidoBairro(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            session.itensPedido.clear();
            session.estado = "MENU";
            return "\u274C Pedido cancelado.\n\n" + getMenuPrincipal();
        }

        try {
            int opcao = Integer.parseInt(msg);
            if (opcao < 1 || opcao > session.bairrosNomes.size()) {
                return "\u26A0\uFE0F Opcao invalida. Escolha um numero de *1* a *" + session.bairrosNomes.size() + "*\n\n*0* - Cancelar pedido";
            }

            int idx = opcao - 1;
            session.bairroEntregaId = session.bairrosCache.get(idx)[0];
            session.bairroEntregaNome = session.bairrosNomes.get(idx);
            session.taxaEntrega = session.bairrosTaxas.get(idx);

            session.estado = "PEDIDO_CONFIRMA";
            return gerarResumoPedidoComDados(session);

        } catch (NumberFormatException e) {
            return "\u26A0\uFE0F Digite apenas o *numero* do bairro.\n\n*0* - Cancelar pedido";
        }
    }

    /**
     * Processa a confirmacao de bairro salvo.
     */
    private String processarPedidoConfirmaBairro(ConversationSession session, String msg) {
        switch (msg) {
            case "1":
                // Usar mesmo bairro salvo
                session.bairroEntregaId = session.bairroSalvoId;
                session.bairroEntregaNome = session.bairroSalvo;
                session.taxaEntrega = session.taxaSalva;
                session.estado = "PEDIDO_CONFIRMA";
                return gerarResumoPedidoComDados(session);

            case "2":
                // Escolher outro bairro
                return listarBairrosParaSelecao(session);

            case "3":
                session.itensPedido.clear();
                session.estado = "MENU";
                return "\u274C Pedido cancelado.\n\n" + getMenuPrincipal();

            default:
                return "\u26A0\uFE0F Opcao invalida.\n\n"
                        + "*1* - \u2705 Sim, mesmo bairro\n"
                        + "*2* - \u270F\uFE0F Nao, escolher outro\n"
                        + "*3* - \u274C Cancelar pedido";
        }
    }

    private String processarPedidoConfirma(ConversationSession session, String msg) {
        switch (msg) {
            case "1":
                return finalizarPedido(session);
            case "2":
                session.itensPedido.clear();
                session.estado = "MENU";
                return "\u274C Pedido cancelado.\n\n" + getMenuPrincipal();
            case "3":
                return listarCategoriasParaPedido(session);
            default:
                return "\u26A0\uFE0F Opcao invalida.\n\n*1* - Confirmar pedido\n*2* - Cancelar\n*3* - Editar itens";
        }
    }

    private String processarConsultaPedido(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            session.estado = "MENU";
            return getMenuPrincipal();
        }
        try {
            int vendaId = Integer.parseInt(msg.replaceAll("[^0-9]", ""));
            return consultarVenda(vendaId);
        } catch (NumberFormatException e) {
            return "\u26A0\uFE0F Digite apenas o numero do pedido.\n\n*0* - Voltar ao menu";
        }
    }

    private String processarAtendente(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            session.estado = "MENU";
            return getMenuPrincipal();
        }
        return "\uD83D\uDCDD Mensagem registrada e encaminhada.\n"
                + "Um atendente respondera em breve.\n\n"
                + "Continue descrevendo ou:\n"
                + "*0* - Voltar ao menu";
    }

    // ===================== FUNCOES DE BANCO DE DADOS =====================

    private String gerarCatalogo(int pagina) {
        try {
            Connection conn = dbHelper.getConnection();
            int limit = config.getMaxProdutosCatalogo();
            int offset = pagina * limit;

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, codigo, descricao, preco_venda, unidade FROM produtos "
                            + "WHERE ativo = 1 AND preco_venda > 0 ORDER BY descricao ASC LIMIT ? OFFSET ?");
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDCCB *CATALOGO*\n");
            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            int count = 0;
            while (rs.next()) {
                count++;
                String cod = rs.getString("codigo");
                String desc = rs.getString("descricao");
                double preco = rs.getDouble("preco_venda");
                String unidade = rs.getString("unidade");

                sb.append("*").append(rs.getInt("id")).append("* - ");
                if (cod != null && !cod.isEmpty()) sb.append("[").append(cod).append("] ");
                sb.append(desc).append("\n");
                sb.append("   \uD83D\uDCB0 *R$ ").append(FormatUtils.formatMoney(preco)).append("*");
                sb.append("/").append(unidade != null ? unidade : "UN");
                sb.append("\n\n");
            }
            rs.close();
            ps.close();

            if (count == 0) {
                if (pagina == 0) {
                    return "\uD83D\uDCCB Catalogo vazio.\n\n*0* - Voltar ao menu";
                } else {
                    return "\uD83D\uDCCB Sem mais produtos.\n\n*8* - Pagina anterior\n*0* - Voltar ao menu";
                }
            }

            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            sb.append("Pag. ").append(pagina + 1).append("\n\n");
            sb.append("Digite o *codigo* do produto p/ detalhes\n");
            sb.append("*9* - Proxima pagina\n");
            if (pagina > 0) sb.append("*8* - Pagina anterior\n");
            sb.append("*0* - Voltar ao menu");

            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar catalogo", e);
            return "Erro ao carregar catalogo. Tente novamente.";
        }
    }

    private String buscarProdutosCatalogo(String busca) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, codigo, descricao, preco_venda, unidade FROM produtos "
                            + "WHERE ativo = 1 AND (descricao LIKE ? OR codigo LIKE ? OR codigo_barras LIKE ?) "
                            + "ORDER BY descricao ASC LIMIT 10");
            String like = "%" + busca + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDD0D *Resultados:* _").append(busca).append("_\n\n");

            int count = 0;
            while (rs.next()) {
                count++;
                sb.append("*").append(rs.getInt("id")).append("* - ");
                sb.append(rs.getString("descricao")).append("\n");
                sb.append("   \uD83D\uDCB0 *R$ ").append(FormatUtils.formatMoney(rs.getDouble("preco_venda"))).append("*\n\n");
            }
            rs.close();
            ps.close();

            if (count == 0) {
                return "Nenhum produto encontrado para *" + busca + "*.\n\n*0* - Voltar ao menu";
            }

            sb.append("Digite o *codigo* do produto p/ detalhes\n");
            sb.append("*0* - Voltar ao menu");
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar produtos", e);
            return "Erro na busca. Tente novamente.";
        }
    }

    private String gerarDetalheProduto(int produtoId) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.*, tp.descricao as tipo_desc FROM produtos p "
                            + "LEFT JOIN tipos_produto tp ON p.tipo_produto_id = tp.id "
                            + "WHERE p.id = ?");
            ps.setInt(1, produtoId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("\uD83D\uDCE6 *DETALHES DO PRODUTO*\n");
                sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
                sb.append("*").append(rs.getString("descricao")).append("*\n\n");

                String cod = rs.getString("codigo");
                if (cod != null && !cod.isEmpty()) sb.append("Codigo: ").append(cod).append("\n");

                String barras = rs.getString("codigo_barras");
                if (barras != null && !barras.isEmpty()) sb.append("Cod. Barras: ").append(barras).append("\n");

                String tipo = rs.getString("tipo_desc");
                if (tipo != null && !tipo.isEmpty()) sb.append("Categoria: ").append(tipo).append("\n");

                sb.append("Unidade: ").append(rs.getString("unidade")).append("\n");
                sb.append("\uD83D\uDCB0 *Preco: R$ ").append(FormatUtils.formatMoney(rs.getDouble("preco_venda"))).append("*\n");

                sb.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                sb.append("*0* - Voltar ao menu");

                rs.close();
                ps.close();
                return sb.toString();
            }

            rs.close();
            ps.close();
            return "Produto nao encontrado.\n\n*0* - Voltar ao menu";

        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar detalhe do produto", e);
            return "Erro ao buscar produto. Tente novamente.";
        }
    }

    private String buscarPreco(String busca) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT descricao, preco_venda, unidade FROM produtos "
                            + "WHERE ativo = 1 AND (descricao LIKE ? OR codigo LIKE ? OR codigo_barras LIKE ?) "
                            + "ORDER BY descricao ASC LIMIT 10");
            String like = "%" + busca + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDCB0 *Precos:*\n\n");

            int count = 0;
            while (rs.next()) {
                count++;
                String unidade = rs.getString("unidade");
                sb.append("\u2022 ").append(rs.getString("descricao")).append("\n");
                sb.append("   *R$ ").append(FormatUtils.formatMoney(rs.getDouble("preco_venda"))).append("*");
                sb.append("/").append(unidade != null ? unidade : "UN");
                sb.append("\n\n");
            }
            rs.close();
            ps.close();

            if (count == 0) {
                return "Nenhum produto encontrado para *" + busca + "*.\n\nDigite outro nome ou *0* p/ menu.";
            }

            sb.append("\nDigite outro nome para nova consulta\n*0* - Voltar ao menu");
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar preco", e);
            return "Erro na consulta. Tente novamente.";
        }
    }



    private String adicionarItemPedido(ConversationSession session, int produtoId, double quantidade) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, descricao, preco_venda FROM produtos WHERE id = ? AND ativo = 1");
            ps.setInt(1, produtoId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String desc = rs.getString("descricao");
                double preco = rs.getDouble("preco_venda");

                rs.close();
                ps.close();

                PedidoItem item = new PedidoItem(produtoId, desc, quantidade, preco);
                session.itensPedido.add(item);
                session.estado = "PEDIDO_CONTINUAR";

                double totalCarrinho = 0;
                for (PedidoItem pi : session.itensPedido) totalCarrinho += pi.total;

                return "\u2705 *Adicionado ao carrinho:*\n\n"
                        + "\u2022 " + desc + "\n"
                        + "  Qtd: " + FormatUtils.formatQuantidade(quantidade) + " x R$ " + FormatUtils.formatMoney(preco) + "\n"
                        + "  Subtotal: *R$ " + FormatUtils.formatMoney(item.total) + "*\n\n"
                        + "\uD83D\uDED2 Itens: *" + session.itensPedido.size() + "*\n"
                        + "\uD83D\uDCB0 Total: *R$ " + FormatUtils.formatMoney(totalCarrinho) + "*\n\n"
                        + "*1* - \uD83D\uDED2 Continuar comprando\n"
                        + "*2* - \u2705 Finalizar pedido\n"
                        + "*3* - \u274C Cancelar pedido";
            }

            rs.close();
            ps.close();
            return "Produto nao encontrado.";

        } catch (Exception e) {
            Log.e(TAG, "Erro ao adicionar item", e);
            return "Erro ao adicionar produto. Tente novamente.";
        }
    }

    /**
     * v4.0 - Gera resumo do pedido incluindo dados do cliente.
     * v6.1.0 - Bairro e taxa de entrega OBRIGATORIOS no resumo.
     */
    private String gerarResumoPedidoComDados(ConversationSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCB *RESUMO DO PEDIDO*\n");
        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

        // Dados do cliente
        sb.append("\uD83D\uDC64 *Cliente:* ").append(session.clienteNome != null ? session.clienteNome : "N/A").append("\n");
        if (session.celularWhatsApp != null && !session.celularWhatsApp.isEmpty()) {
            sb.append("\uD83D\uDCF1 *Celular:* ").append(session.celularWhatsApp).append("\n");
        }
        if (session.enderecoCompleto != null && !session.enderecoCompleto.isEmpty()) {
            sb.append("\uD83D\uDCCD *Endereco:* ").append(session.enderecoCompleto).append("\n");
        }
        // v6.1.0 - Bairro de entrega OBRIGATORIO
        sb.append("\uD83D\uDE9A *Bairro:* ").append(session.bairroEntregaNome != null && !session.bairroEntregaNome.isEmpty() ? session.bairroEntregaNome : "N/A").append("\n");
        sb.append("\n");

        // Itens do pedido
        double subtotal = 0;
        int num = 1;
        for (PedidoItem item : session.itensPedido) {
            sb.append(num++).append(". ").append(item.descricao).append("\n");
            sb.append("   ").append(FormatUtils.formatQuantidade(item.quantidade));
            sb.append(" x R$ ").append(FormatUtils.formatMoney(item.precoUnitario));
            sb.append(" = *R$ ").append(FormatUtils.formatMoney(item.total)).append("*\n");
            // v6.3.0 - Exibir adicionais do item
            if (item.adicionais != null && !item.adicionais.isEmpty()) {
                for (AdicionalSelecionado ad : item.adicionais) {
                    sb.append("   \u2795 ").append(ad.descricao);
                    if (ad.preco > 0) {
                        sb.append(" (+R$ ").append(FormatUtils.formatMoney(ad.preco)).append(")");
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
            subtotal += item.getTotalComAdicionais();
        }

        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        sb.append("\uD83D\uDED2 Subtotal Produtos: R$ ").append(FormatUtils.formatMoney(subtotal)).append("\n");
        sb.append("\uD83D\uDE9A Taxa Entrega (" + (session.bairroEntregaNome != null ? session.bairroEntregaNome : "") + "): R$ ").append(FormatUtils.formatMoney(session.taxaEntrega)).append("\n");
        double totalFinal = subtotal + session.taxaEntrega;
        sb.append("\uD83D\uDCB0 *TOTAL GERAL: R$ ").append(FormatUtils.formatMoney(totalFinal)).append("*\n\n");
        sb.append("*1* - \u2705 Confirmar pedido\n");
        sb.append("*2* - \u274C Cancelar\n");
        sb.append("*3* - \u270F\uFE0F Editar itens");

        return sb.toString();
    }

    private String gerarResumoPedido(ConversationSession session) {
        return gerarResumoPedidoComDados(session);
    }

    /**
     * v4.0 - Finaliza o pedido vinculando ao cliente pelo celular do WhatsApp.
     * Cria ou atualiza o cliente no banco de dados.
     * v6.1.0 - Valida obrigatoriamente que um bairro foi selecionado.
     */
    private String finalizarPedido(ConversationSession session) {
        try {
            // v6.1.0 - Validar que bairro foi selecionado (obrigatorio)
            if (session.bairroEntregaId <= 0 || session.bairroEntregaNome == null || session.bairroEntregaNome.isEmpty()) {
                // Bairro nao selecionado - redirecionar para selecao
                return iniciarSelecaoBairro(session);
            }

            Connection conn = dbHelper.getConnection();

            // v4.0 - Criar ou atualizar cliente no banco
            int clienteId = salvarOuAtualizarCliente(conn, session);

            double subtotal = 0;
            for (PedidoItem item : session.itensPedido) subtotal += item.getTotalComAdicionais();
            double totalComTaxa = subtotal + session.taxaEntrega;

            // Montar observacao com dados do cliente
            StringBuilder obs = new StringBuilder();
            obs.append("Pedido via WhatsApp Bot");
            obs.append(" | Celular: ").append(session.celularWhatsApp != null ? session.celularWhatsApp : session.contato);
            if (session.clienteNome != null && !session.clienteNome.isEmpty()) {
                obs.append(" | Cliente: ").append(session.clienteNome);
            }
            if (session.enderecoCompleto != null && !session.enderecoCompleto.isEmpty()) {
                obs.append(" | Endereco: ").append(session.enderecoCompleto);
            }
            if (session.bairroEntregaNome != null && !session.bairroEntregaNome.isEmpty()) {
                obs.append(" | Bairro: ").append(session.bairroEntregaNome);
                obs.append(" | Taxa Entrega: R$ ").append(FormatUtils.formatMoney(session.taxaEntrega));
            }

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vendas (cliente_id, data_venda, total_bruto, total_liquido, status, observacao, celular_whatsapp, "
                            + "para_entrega, taxa_entrega, bairro_entrega, endereco_entrega) "
                            + "VALUES (?, NOW(), ?, ?, 'pendente', ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, clienteId);
            ps.setDouble(2, subtotal);
            ps.setDouble(3, totalComTaxa);
            ps.setString(4, obs.toString());
            ps.setString(5, session.celularWhatsApp != null ? session.celularWhatsApp : session.contato);
            ps.setInt(6, 1); // para_entrega = true
            ps.setDouble(7, session.taxaEntrega);
            ps.setString(8, session.bairroEntregaNome != null ? session.bairroEntregaNome : "");
            ps.setString(9, session.enderecoCompleto != null ? session.enderecoCompleto : "");
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int vendaId = 0;
            if (keys.next()) vendaId = keys.getInt(1);
            keys.close();
            ps.close();

            for (PedidoItem item : session.itensPedido) {
                ps = conn.prepareStatement(
                        "INSERT INTO itens_venda (venda_id, produto_id, descricao_produto, quantidade, preco_unitario, total) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, vendaId);
                ps.setInt(2, item.produtoId);
                // v6.3.0 - Incluir adicionais na descricao do item
                String descComAdicionais = item.descricao;
                if (item.adicionais != null && !item.adicionais.isEmpty()) {
                    StringBuilder adDesc = new StringBuilder();
                    for (AdicionalSelecionado ad : item.adicionais) {
                        if (adDesc.length() > 0) adDesc.append(", ");
                        adDesc.append(ad.descricao);
                    }
                    descComAdicionais += " [" + adDesc.toString() + "]";
                }
                ps.setString(3, descComAdicionais);
                ps.setDouble(4, item.quantidade);
                ps.setDouble(5, item.precoUnitario);
                ps.setDouble(6, item.getTotalComAdicionais());
                ps.executeUpdate();

                // v6.3.0 - Salvar adicionais do item na tabela itens_venda_adicionais
                ResultSet itemKeys = ps.getGeneratedKeys();
                int itemVendaId = 0;
                if (itemKeys.next()) itemVendaId = itemKeys.getInt(1);
                itemKeys.close();
                ps.close();

                if (itemVendaId > 0 && item.adicionais != null) {
                    for (AdicionalSelecionado ad : item.adicionais) {
                        ps = conn.prepareStatement(
                            "INSERT INTO itens_venda_adicionais (item_venda_id, adicional_id, descricao_adicional, preco) VALUES (?, ?, ?, ?)");
                        ps.setInt(1, itemVendaId);
                        ps.setInt(2, ad.adicionalId);
                        ps.setString(3, ad.descricao);
                        ps.setDouble(4, ad.preco);
                        ps.executeUpdate();
                        ps.close();
                    }
                }
            }

            logger.logPedido(session.contato, vendaId, FormatUtils.formatMoney(totalComTaxa));

            // v4.6.0 - Impressao automatica do pedido na impressora configurada
            imprimirPedidoAutomatico(vendaId);

            // v4.5 - Salvar bairro vinculado ao celular para memoria
            if (session.bairroEntregaId > 0 && session.celularWhatsApp != null && !session.celularWhatsApp.isEmpty()) {
                try {
                    salvarBairroCliente(conn, session.celularWhatsApp, session.bairroEntregaId);
                } catch (Exception ignored) {
                    Log.w(TAG, "Erro ao salvar bairro do cliente: " + ignored.getMessage());
                }
            }

            session.itensPedido.clear();
            session.estado = "MENU";

            String resposta = config.processarTemplatePedido(
                    config.getMsgPedidoRecebido(), vendaId, FormatUtils.formatMoney(totalComTaxa));

            // v6.1.0 - Adicionar dados do cliente na confirmacao (bairro e taxa sempre visiveis)
            StringBuilder confirmacao = new StringBuilder();
            confirmacao.append(resposta);
            confirmacao.append("\n\n\uD83D\uDC64 *").append(session.clienteNome != null ? session.clienteNome : "Cliente").append("*");
            if (session.enderecoCompleto != null && !session.enderecoCompleto.isEmpty()) {
                confirmacao.append("\n\uD83D\uDCCD ").append(session.enderecoCompleto);
            }
            confirmacao.append("\n\uD83D\uDE9A Bairro: ").append(session.bairroEntregaNome != null ? session.bairroEntregaNome : "N/A");
            confirmacao.append("\n\uD83D\uDCB0 Total c/ entrega: *R$ ").append(FormatUtils.formatMoney(totalComTaxa)).append("*");
            if (session.celularWhatsApp != null && !session.celularWhatsApp.isEmpty()) {
                confirmacao.append("\n\uD83D\uDCF1 ").append(session.celularWhatsApp);
            }
            confirmacao.append("\n\n").append(getMenuCompacto());

            return confirmacao.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao finalizar pedido", e);
            logger.logErro("Erro ao finalizar pedido", e);
            return "\u274C Erro ao registrar pedido. Tente novamente.\n\n*1* - Tentar novamente\n*0* - Voltar ao menu";
        }
    }

    // ===================== v4.5 - PERSISTENCIA DO BAIRRO POR CELULAR =====================

    /**
     * Salva ou atualiza o bairro vinculado ao celular do cliente.
     * Na proxima compra, o sistema lembrara o bairro escolhido.
     */
    private void salvarBairroCliente(Connection conn, String celular, int bairroId) throws Exception {
        String celLimpo = celular.replaceAll("[^0-9]", "");

        // Verificar se ja existe registro
        PreparedStatement psCheck = conn.prepareStatement(
            "SELECT id FROM cliente_bairro_whatsapp WHERE celular_whatsapp LIKE ?");
        psCheck.setString(1, "%" + celLimpo + "%");
        ResultSet rsCheck = psCheck.executeQuery();

        if (rsCheck.next()) {
            int existeId = rsCheck.getInt("id");
            rsCheck.close();
            psCheck.close();
            // Atualizar
            PreparedStatement psUpdate = conn.prepareStatement(
                "UPDATE cliente_bairro_whatsapp SET bairro_id = ?, data_atualizacao = NOW() WHERE id = ?");
            psUpdate.setInt(1, bairroId);
            psUpdate.setInt(2, existeId);
            psUpdate.executeUpdate();
            psUpdate.close();
        } else {
            rsCheck.close();
            psCheck.close();
            // Inserir novo
            PreparedStatement psInsert = conn.prepareStatement(
                "INSERT INTO cliente_bairro_whatsapp (celular_whatsapp, bairro_id, data_atualizacao) VALUES (?, ?, NOW())");
            psInsert.setString(1, celLimpo);
            psInsert.setInt(2, bairroId);
            psInsert.executeUpdate();
            psInsert.close();
        }

        Log.d(TAG, "Bairro salvo para celular " + celLimpo + ": bairroId=" + bairroId);
    }

    // ===================== v4.0 - FUNCOES DE PERSISTENCIA DO CLIENTE =====================

    /**
     * Classe auxiliar para dados do cliente.
     */
    private static class ClienteDados {
        int id;
        String nome;
        String celular;
        String endereco;
        String numero;
        String bairro;
        String cidade;
        String uf;
        String cep;
        String enderecoCompleto;
    }

    /**
     * Busca um cliente pelo numero de celular (WhatsApp).
     * Tenta varias formas de match: com e sem codigo do pais.
     */
    private ClienteDados buscarClientePorCelular(String celular) {
        try {
            Connection conn = dbHelper.getConnection();

            // Normalizar celular - remover tudo exceto digitos
            String celularLimpo = celular.replaceAll("[^0-9]", "");

            // Tentar buscar com varias variantes do numero
            List<String> variantes = new ArrayList<>();
            variantes.add(celularLimpo);
            if (celularLimpo.startsWith("55") && celularLimpo.length() > 10) {
                variantes.add(celularLimpo.substring(2)); // Sem codigo do pais
            }
            if (!celularLimpo.startsWith("55")) {
                variantes.add("55" + celularLimpo); // Com codigo do pais
            }

            for (String variante : variantes) {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, nome, celular, endereco, numero, bairro, cidade, uf, cep "
                                + "FROM clientes WHERE ativo = 1 AND "
                                + "(REPLACE(REPLACE(REPLACE(REPLACE(celular, '(', ''), ')', ''), '-', ''), ' ', '') LIKE ? "
                                + "OR celular LIKE ?) "
                                + "ORDER BY id DESC LIMIT 1");
                ps.setString(1, "%" + variante + "%");
                ps.setString(2, "%" + variante + "%");
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    ClienteDados dados = new ClienteDados();
                    dados.id = rs.getInt("id");
                    dados.nome = rs.getString("nome");
                    dados.celular = rs.getString("celular");
                    dados.endereco = rs.getString("endereco");
                    dados.numero = rs.getString("numero");
                    dados.bairro = rs.getString("bairro");
                    dados.cidade = rs.getString("cidade");
                    dados.uf = rs.getString("uf");
                    dados.cep = rs.getString("cep");

                    // Montar endereco completo
                    StringBuilder endCompleto = new StringBuilder();
                    if (dados.endereco != null && !dados.endereco.isEmpty()) {
                        endCompleto.append(dados.endereco);
                        if (dados.numero != null && !dados.numero.isEmpty()) {
                            endCompleto.append(", ").append(dados.numero);
                        }
                        if (dados.bairro != null && !dados.bairro.isEmpty()) {
                            endCompleto.append(" - ").append(dados.bairro);
                        }
                        if (dados.cidade != null && !dados.cidade.isEmpty()) {
                            endCompleto.append(", ").append(dados.cidade);
                        }
                        if (dados.uf != null && !dados.uf.isEmpty()) {
                            endCompleto.append("/").append(dados.uf);
                        }
                        if (dados.cep != null && !dados.cep.isEmpty()) {
                            endCompleto.append(" - CEP: ").append(dados.cep);
                        }
                    }
                    dados.enderecoCompleto = endCompleto.toString();

                    rs.close();
                    ps.close();

                    // Ignorar cliente padrao "Cliente nao informado"
                    if (dados.nome != null && dados.nome.equalsIgnoreCase("Cliente nao informado")) {
                        return null;
                    }

                    return dados;
                }
                rs.close();
                ps.close();
            }

            return null;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar cliente por celular", e);
            return null;
        }
    }

    /**
     * Salva um novo cliente ou atualiza o existente no banco de dados.
     * Retorna o ID do cliente.
     */
    private int salvarOuAtualizarCliente(Connection conn, ConversationSession session) {
        try {
            String celular = session.celularWhatsApp != null ? session.celularWhatsApp : session.contato;
            String nome = session.clienteNome != null ? session.clienteNome : "Cliente WhatsApp";
            String endereco = session.enderecoCompleto != null ? session.enderecoCompleto : "";

            // Se ja tem um cliente existente identificado, atualizar
            if (session.clienteExistente && session.clienteIdEncontrado > 0) {
                // Atualizar endereco se foi informado um novo
                if (endereco != null && !endereco.isEmpty()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE clientes SET nome = ?, endereco = ?, celular = ? WHERE id = ?");
                    ps.setString(1, nome);
                    ps.setString(2, endereco);
                    ps.setString(3, celular);
                    ps.setInt(4, session.clienteIdEncontrado);
                    ps.executeUpdate();
                    ps.close();
                }
                return session.clienteIdEncontrado;
            }

            // Verificar se ja existe pelo celular (dupla verificacao)
            ClienteDados existente = buscarClientePorCelular(celular);
            if (existente != null) {
                // Atualizar dados
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE clientes SET nome = ?, endereco = ? WHERE id = ?");
                ps.setString(1, nome);
                ps.setString(2, endereco);
                ps.setInt(3, existente.id);
                ps.executeUpdate();
                ps.close();
                return existente.id;
            }

            // Criar novo cliente
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO clientes (nome, celular, endereco, ativo) VALUES (?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, nome);
            ps.setString(2, celular);
            ps.setString(3, endereco);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int novoId = 0;
            if (keys.next()) novoId = keys.getInt(1);
            keys.close();
            ps.close();

            Log.d(TAG, "Novo cliente criado: ID=" + novoId + ", Nome=" + nome + ", Celular=" + celular);
            return novoId;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar/atualizar cliente", e);
            return 0; // Retorna 0 se falhar (cliente_id NULL na venda)
        }
    }

    /**
     * Extrai o numero de celular do nome do contato do WhatsApp.
     * O contato pode vir como nome salvo ou como numero.
     */
    private String extrairCelularDoContato(String contato) {
        if (contato == null || contato.isEmpty()) return "";

        // Se o contato ja e um numero de telefone
        String apenasDigitos = contato.replaceAll("[^0-9]", "");
        if (apenasDigitos.length() >= 10) {
            // E um numero de telefone
            if (!apenasDigitos.startsWith("55") && apenasDigitos.length() <= 11) {
                apenasDigitos = "55" + apenasDigitos;
            }
            return apenasDigitos;
        }

        // Se contem numeros suficientes dentro do texto
        if (apenasDigitos.length() >= 8) {
            return apenasDigitos;
        }

        // E um nome de contato - retornar vazio (sera preenchido pelo contato)
        return contato;
    }

    private String consultarVenda(int vendaId) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT v.*, c.nome as cliente_nome FROM vendas v "
                            + "LEFT JOIN clientes c ON v.cliente_id = c.id WHERE v.id = ?");
            ps.setInt(1, vendaId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("\uD83D\uDCCB *PEDIDO #").append(vendaId).append("*\n");
                sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

                String status = rs.getString("status");
                String emoji;
                switch (status != null ? status : "") {
                    case "finalizada": emoji = "\u2705"; break;
                    case "pendente": emoji = "\u23F3"; break;
                    case "cancelada": emoji = "\u274C"; break;
                    default: emoji = "\uD83D\uDCCB"; break;
                }

                sb.append(emoji).append(" Status: *").append(status != null ? status.toUpperCase() : "N/A").append("*\n");
                sb.append("Data: ").append(FormatUtils.formatDate(rs.getString("data_venda"))).append("\n");

                // v4.0 - Mostrar dados do cliente
                String clienteNome = rs.getString("cliente_nome");
                if (clienteNome != null && !clienteNome.isEmpty() && !clienteNome.equals("Cliente nao informado")) {
                    sb.append("\uD83D\uDC64 Cliente: *").append(clienteNome).append("*\n");
                }

                // v4.0 - Mostrar celular do WhatsApp
                try {
                    String celularWpp = rs.getString("celular_whatsapp");
                    if (celularWpp != null && !celularWpp.isEmpty()) {
                        sb.append("\uD83D\uDCF1 Celular: ").append(celularWpp).append("\n");
                    }
                } catch (Exception ignored) {
                    // Coluna pode nao existir em versoes anteriores
                }

                sb.append("\uD83D\uDCB0 Total: *R$ ").append(FormatUtils.formatMoney(rs.getDouble("total_liquido"))).append("*\n");

                String obs = rs.getString("observacao");
                if (obs != null && !obs.isEmpty()) {
                    // Extrair endereco da observacao se disponivel
                    if (obs.contains("Endereco: ")) {
                        String endereco = obs.substring(obs.indexOf("Endereco: ") + 10);
                        if (endereco.contains(" | ")) {
                            endereco = endereco.substring(0, endereco.indexOf(" | "));
                        }
                        sb.append("\uD83D\uDCCD Endereco: ").append(endereco).append("\n");
                    }
                }

                rs.close();
                ps.close();

                ps = conn.prepareStatement("SELECT * FROM itens_venda WHERE venda_id = ?");
                ps.setInt(1, vendaId);
                rs = ps.executeQuery();

                sb.append("\n*Itens:*\n");
                while (rs.next()) {
                    sb.append("\u2022 ").append(rs.getString("descricao_produto"));
                    sb.append(" (").append(FormatUtils.formatQuantidade(rs.getDouble("quantidade"))).append("x)");
                    sb.append(" R$ ").append(FormatUtils.formatMoney(rs.getDouble("total"))).append("\n");
                }
                rs.close();
                ps.close();

                sb.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                sb.append("*0* - Voltar ao menu");
                return sb.toString();
            }

            rs.close();
            ps.close();
            return "Pedido #" + vendaId + " nao encontrado.\n\nDigite outro numero ou *0* p/ menu.";

        } catch (Exception e) {
            Log.e(TAG, "Erro ao consultar venda", e);
            return "Erro ao consultar pedido. Tente novamente.";
        }
    }

    private String gerarInfoContato(ConversationSession session) {
        try {
            Connection conn = dbHelper.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM empresa LIMIT 1");

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDCCD *CONTATO*\n");
            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            if (rs.next()) {
                String nome = rs.getString("nome_fantasia");
                if (nome == null || nome.isEmpty()) nome = rs.getString("razao_social");
                sb.append("\uD83C\uDFE2 *").append(nome != null ? nome : config.getNomeEmpresa()).append("*\n\n");

                String cnpj = rs.getString("cnpj");
                if (cnpj != null && !cnpj.isEmpty()) sb.append("CNPJ: ").append(cnpj).append("\n");

                String endereco = rs.getString("endereco");
                if (endereco != null && !endereco.isEmpty()) {
                    sb.append("\uD83D\uDCCD ").append(endereco);
                    String num = rs.getString("numero");
                    if (num != null && !num.isEmpty()) sb.append(", ").append(num);
                    String bairro = rs.getString("bairro");
                    if (bairro != null && !bairro.isEmpty()) sb.append(" - ").append(bairro);
                    String cidade = rs.getString("cidade");
                    if (cidade != null && !cidade.isEmpty()) sb.append("\n   ").append(cidade);
                    String uf = rs.getString("uf");
                    if (uf != null && !uf.isEmpty()) sb.append("/").append(uf);
                    String cep = rs.getString("cep");
                    if (cep != null && !cep.isEmpty()) sb.append(" - CEP: ").append(cep);
                    sb.append("\n");
                }

                String tel = rs.getString("telefone");
                if (tel != null && !tel.isEmpty()) sb.append("Tel: ").append(tel).append("\n");

                String email = rs.getString("email");
                if (email != null && !email.isEmpty()) sb.append("Email: ").append(email).append("\n");
            } else {
                sb.append("\uD83C\uDFE2 *").append(config.getNomeEmpresa()).append("*\n");
            }

            rs.close();
            stmt.close();

            sb.append("\n\u23F0 *Horario:* ").append(config.getHorarioInicio()).append("h as ").append(config.getHorarioFim()).append("h\n");
            sb.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
            sb.append(getMenuCompacto());

            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar info contato", e);
            return "\uD83D\uDCCD *" + config.getNomeEmpresa() + "*\n\n"
                    + "\u23F0 Horario: " + config.getHorarioInicio() + "h as " + config.getHorarioFim() + "h\n\n"
                    + getMenuCompacto();
        }
    }

    // ===================== FUNCOES AUXILIARES =====================

    /**
     * Menu principal completo com todas as opcoes numeradas.
     */
    private String getMenuPrincipal() {
        return "Digite o numero da opcao:\n\n"
                + "*0* - \u274C *Encerrar*\n"
                + "*2* - \uD83D\uDCB0 Precos\n"
                + "*3* - \uD83D\uDED2 Pedido\n"
                + "*4* - \uD83D\uDCDD Consultar Pedido\n"
                + "*5* - \uD83D\uDCCD Contato\n"
                + "*6* - \uD83D\uDC64 Atendente\n"
                + "*7* - \uD83D\uDCCD Rastrear Entregador\n"
                + "*1* - \uD83D\uDCCB Catalogo";
    }

    /**
     * Menu compacto em uma linha para uso apos respostas.
     */
    private String getMenuCompacto() {
        return "*0*-*Encerrar* *2*-Precos *3*-Pedido *4*-Consultar *5*-Contato *6*-Atendente *7*-Rastrear *1*-Catalogo";
    }

    /**
     * v5.0.0 - Interpreta a intencao do usuario usando IA + heuristicas.
     * Primeiro tenta classificacao com IA, depois fallback para regex.
     */
    private String interpretarIntencao(ConversationSession session, String msg) {
        if (pareceRespostaDoBot(msg)) {
            return null;
        }

        // v5.0.0 - Usar IA para classificar intencao
        if (config.isIAEnabled() && config.isIAInterpretarIntencao()) {
            try {
                WhatsAppAIHelper.AIIntentResult intentResult = aiHelper.classificarIntencao(msg, session.estado);
                if (intentResult != null && intentResult.confidence >= 0.70) {
                    String resposta = processarIntencaoIA(session, intentResult, msg);
                    if (resposta != null) {
                        return resposta;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro na classificacao IA, usando fallback", e);
            }
        }

        // Fallback: classificacao por regex (original otimizado)
        return interpretarIntencaoLocal(session, msg);
    }

    /**
     * v5.0.0 - Processa uma intencao classificada pela IA.
     */
    private String processarIntencaoIA(ConversationSession session, WhatsAppAIHelper.AIIntentResult intent, String msgOriginal) {
        // Detectar frustracao primeiro
        if (config.isIAEnabled() && config.isIADetectarFrustracao() && intent.needsHuman) {
            return aiHelper.gerarRespostaFrustracao(msgOriginal, session.clienteNome);
        }

        switch (intent.intent) {
            case WhatsAppAIHelper.INTENT_GREETING:
                return config.processarTemplate(config.getMsgBoasVindas());

            case WhatsAppAIHelper.INTENT_CATALOG:
                session.estado = "CATALOGO";
                session.paginaCatalogo = 0;
                return gerarCatalogo(0);

            case WhatsAppAIHelper.INTENT_PRICE:
                session.estado = "PRECO";
                if (intent.productSearch != null && !intent.productSearch.isEmpty()) {
                    return buscarPreco(intent.productSearch);
                }
                return "\uD83D\uDCB0 *Consulta de Preco*\n\nDigite o nome do produto:\n\n*0* - Voltar ao menu";

            case WhatsAppAIHelper.INTENT_ORDER:
                session.itensPedido.clear();
                // Se mencionou um produto especifico, buscar direto
                if (intent.productSearch != null && !intent.productSearch.isEmpty()) {
                    String buscaResult = buscarProdutoParaPedidoInteligente(session, intent.productSearch);
                    if (buscaResult != null) return buscaResult;
                }
                return listarCategoriasParaPedido(session);

            case WhatsAppAIHelper.INTENT_ORDER_STATUS:
                session.estado = "CONSULTA_PEDIDO";
                return "\uD83D\uDCCB *Consultar Pedido*\n\nDigite o numero do pedido:\n\n*0* - Voltar ao menu";

            case WhatsAppAIHelper.INTENT_CONTACT:
                return gerarInfoContato(session);

            case WhatsAppAIHelper.INTENT_HUMAN:
            case WhatsAppAIHelper.INTENT_COMPLAINT:
                session.estado = "ATENDENTE";
                if (intent.intent.equals(WhatsAppAIHelper.INTENT_COMPLAINT)) {
                    return aiHelper.gerarRespostaFrustracao(msgOriginal, session.clienteNome);
                }
                return "\uD83D\uDC64 *Atendimento Humano*\n\n"
                        + "Descreva o motivo do seu contato.\n"
                        + "Um atendente respondera em breve.\n\n"
                        + "*0* - Voltar ao menu";

            case WhatsAppAIHelper.INTENT_TRACK:
                session.estado = "RASTREAR_ENTREGADOR";
                return "\uD83D\uDCCD *Rastrear Entregador*\n\n"
                        + "Digite o numero do pedido\n"
                        + "ou *99* para ver entregadores ativos.\n\n"
                        + "*0* - Voltar ao menu";

            case WhatsAppAIHelper.INTENT_THANKS:
                // Resposta inteligente de agradecimento
                if (config.isIAEnabled() && config.isIARespostasInteligentes()) {
                    String respostaIA = aiHelper.gerarRespostaInteligente(
                            msgOriginal, session.clienteNome, session.estado, "cliente agradecendo");
                    if (respostaIA != null) {
                        return respostaIA + "\n\n" + getMenuCompacto();
                    }
                }
                return "De nada! Estamos a disposicao. \uD83D\uDE0A\n\n" + getMenuCompacto();

            case WhatsAppAIHelper.INTENT_GOODBYE:
                session.estado = "ENCERRADO";
                return config.processarTemplate(config.getMsgEncerramento());

            case WhatsAppAIHelper.INTENT_HELP:
                return "\uD83D\uDCA1 *Como posso ajudar?*\n\n"
                        + "Escolha uma opcao digitando o numero:\n\n"
                        + getMenuPrincipal();

            case WhatsAppAIHelper.INTENT_PRODUCT_SEARCH:
                // Buscar produto diretamente
                if (intent.productSearch != null && !intent.productSearch.isEmpty()) {
                    session.estado = "PRECO";
                    return buscarPreco(intent.productSearch);
                }
                break;

            case WhatsAppAIHelper.INTENT_UNKNOWN:
                // Usar IA para gerar resposta inteligente
                if (config.isIAEnabled() && config.isIARespostasInteligentes()) {
                    String respostaIA = aiHelper.gerarRespostaInteligente(
                            msgOriginal, session.clienteNome, session.estado, null);
                    if (respostaIA != null) {
                        return respostaIA;
                    }
                }
                break;
        }

        return null;
    }

    /**
     * v5.0.0 - Busca inteligente de produto para pedido direto.
     * Quando o cliente diz "quero uma pizza" a IA busca o produto.
     */
    private String buscarProdutoParaPedidoInteligente(ConversationSession session, String busca) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, descricao, preco_venda, tipo_produto_id FROM produtos "
                            + "WHERE ativo = 1 AND preco_venda > 0 AND descricao LIKE ? "
                            + "ORDER BY descricao ASC LIMIT 5");
            ps.setString(1, "%" + busca + "%");
            ResultSet rs = ps.executeQuery();

            List<int[]> resultados = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDD0D *Encontrei estes produtos:*\n");
            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            int num = 0;
            while (rs.next()) {
                num++;
                int prodId = rs.getInt("id");
                String desc = rs.getString("descricao");
                double preco = rs.getDouble("preco_venda");
                resultados.add(new int[]{prodId, num});
                sb.append("*").append(num).append("* - ").append(desc);
                sb.append(" | R$ ").append(FormatUtils.formatMoney(preco)).append("\n");
            }
            rs.close();
            ps.close();

            if (num > 0) {
                session.produtosCache.clear();
                session.produtosCache.addAll(resultados);
                session.estado = "PEDIDO_PRODUTOS";
                sb.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                sb.append("Digite o *numero* do produto\n");
                sb.append("*0* - Ver todas as categorias");
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro na busca inteligente", e);
        }
        return null;
    }

    /**
     * Interpretacao de intencao local (fallback sem IA).
     * Versao otimizada do metodo original.
     */
    private String interpretarIntencaoLocal(ConversationSession session, String msg) {
        // Saudacoes
        if (msg.matches("^(oi|ola|olá|bom dia|boa tarde|boa noite|hey|hi|hello|e ai|eai|opa|salve|fala)$")) {
            return config.processarTemplate(config.getMsgBoasVindas());
        }

        // Preco
        if (msg.matches(".*(preco|preço|quanto custa|valor|quanto e|quanto é).*")) {
            session.estado = "PRECO";
            String produto = msg.replaceAll("(preco|preço|quanto custa|valor|quanto e|quanto é|do|da|de|o|a)", "").trim();
            if (!produto.isEmpty()) {
                return buscarPreco(produto);
            }
            return "\uD83D\uDCB0 Digite o nome do produto:\n\n*0* - Voltar ao menu";
        }

        // Pedido
        if (msg.matches(".*(pedido|comprar|quero|pedir|encomenda).*")) {
            session.itensPedido.clear();
            return listarCategoriasParaPedido(session);
        }

        // Rastrear
        if (msg.matches(".*(rastrear|rastreio|rastreamento|entregador|entrega|onde esta|localizar|localizacao|gps).*")) {
            session.estado = "RASTREAR_ENTREGADOR";
            return "\uD83D\uDCCD *Rastrear Entregador*\n\n"
                + "Digite o numero do pedido\n"
                + "ou *99* para ver entregadores ativos.\n\n"
                + "*0* - Voltar ao menu";
        }

        // Catalogo
        if (msg.matches(".*(catalogo|catálogo|cardapio|cardápio|produtos|lista).*")) {
            session.estado = "CATALOGO";
            session.paginaCatalogo = 0;
            return gerarCatalogo(0);
        }

        // Ajuda
        if (msg.matches(".*(ajuda|help|socorro|opcoes|opções).*")) {
            return getMenuPrincipal();
        }

        // Agradecimento
        if (msg.matches(".*(obrigado|obrigada|valeu|thanks|brigado|brigada).*")) {
            return "De nada! Estamos a disposicao.\n\n" + getMenuCompacto();
        }

        // Nao entendeu
        if (msg.length() <= 100) {
            return "\u26A0\uFE0F Opcao invalida. Digite o *numero* desejado:\n\n" + getMenuCompacto();
        }

        return null;
    }

    /**
     * Verifica se uma mensagem parece ser uma resposta gerada pelo proprio bot.
     */
    private boolean pareceRespostaDoBot(String msg) {
        if (msg == null) return false;
        String[] padroesBot = {
            "digite o numero da opcao",
            "catalogo de produtos",
            "consultar preco",
            "fazer pedido",
            "consultar pedido",
            "falar com atendente",
            "rastrear entregador",
            "encerrar atendimento",
            "bem-vindo(a) ao",
            "assistente virtual",
            "resumo do pedido",
            "pedido recebido com sucesso",
            "informacoes de contato",
            "desculpe, nao entendi",
            "desculpe, ocorreu um erro",
            "atendimento humano",
            "sua mensagem foi registrada",
            "precos encontrados",
            "detalhes do produto",
            "adicionado ao carrinho",
            "pedido cancelado",
            "fora do horario de atendimento",
            "prazer atende-lo",
            "escolha uma opcao para continuar",
            "entregadores ativos",
            "rastreamento - pedido",
            "escolha a categoria",
            "escolha o produto",
            "digite a quantidade",
            "continuar comprando",
            "finalizar pedido",
            "cancelar pedido",
            "opcao invalida",
            "voltar ao menu",
            "dados encontrados",
            "dados para entrega",
            "nome completo",
            "endereco completo",
            "mesmo endereco",
            "novos dados para entrega",
            "bairro de entrega",
            "selecione o bairro",
            "mesmo bairro",
            "taxa entrega",
            "\u2501\u2501\u2501\u2501\u2501\u2501"
        };
        for (String padrao : padroesBot) {
            if (msg.contains(padrao)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se o horario atual esta dentro do horario de atendimento.
     */
    private boolean isDentroHorario() {
        try {
            Calendar cal = Calendar.getInstance();
            int horaAtual = cal.get(Calendar.HOUR_OF_DAY);
            int minutoAtual = cal.get(Calendar.MINUTE);
            int minutosAgora = horaAtual * 60 + minutoAtual;

            String[] inicio = config.getHorarioInicio().split(":");
            int minutosInicio = Integer.parseInt(inicio[0]) * 60 + Integer.parseInt(inicio[1]);

            String[] fim = config.getHorarioFim().split(":");
            int minutosFim = Integer.parseInt(fim[0]) * 60 + Integer.parseInt(fim[1]);

            return minutosAgora >= minutosInicio && minutosAgora <= minutosFim;
        } catch (Exception e) {
            return true;
        }
    }

    private void limparSessoesExpiradas() {
        Iterator<Map.Entry<String, ConversationSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ConversationSession> entry = it.next();
            if (entry.getValue().isExpirada()) {
                it.remove();
            }
        }
    }

    public int getSessoesAtivas() {
        limparSessoesExpiradas();
        return sessions.size();
    }

    public void encerrarTodasSessoes() {
        sessions.clear();
    }

    // ===================== RASTREAMENTO DE ENTREGADOR =====================

    private String processarRastrearEntregador(ConversationSession session, String msg) {
        if (msg.equals("0")) {
            session.estado = "MENU";
            return getMenuPrincipal();
        }

        if (msg.equals("99")) {
            return listarEntregadoresAtivos(session);
        }

        try {
            int pedidoId = Integer.parseInt(msg);
            return rastrearEntregadorPorPedido(session, pedidoId);
        } catch (NumberFormatException e) {
            return buscarEntregadorPorNome(session, msg);
        }
    }

    private String listarEntregadoresAtivos(ConversationSession session) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT r.entregador_id, e.nome, r.latitude, r.longitude, r.data_hora "
                + "FROM rastreamento_entregador r "
                + "INNER JOIN entregadores e ON r.entregador_id = e.id "
                + "WHERE r.ativo = 1 AND r.latitude != 0 "
                + "ORDER BY r.data_hora DESC");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDCCD *ENTREGADORES ATIVOS*\n");
            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

            int count = 0;
            while (rs.next()) {
                count++;
                String nome = rs.getString("nome");
                double lat = rs.getDouble("latitude");
                double lng = rs.getDouble("longitude");
                String dataHora = rs.getString("data_hora");

                sb.append("\uD83D\uDEB4 *").append(nome).append("*\n");
                sb.append("\uD83D\uDCCD ").append(String.format("%.6f, %.6f", lat, lng)).append("\n");
                sb.append("\uD83D\uDD50 ").append(FormatUtils.formatDate(dataHora)).append("\n");
                sb.append("\uD83D\uDDFA\uFE0F https://maps.google.com/?q=").append(lat).append(",").append(lng).append("\n\n");
            }
            rs.close();
            ps.close();

            if (count == 0) {
                return "\uD83D\uDCCD Nenhum entregador ativo.\n\n*0* - Voltar ao menu";
            }

            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            sb.append("Digite o numero de um pedido para rastrear\n");
            sb.append("*0* - Voltar ao menu");
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar entregadores ativos", e);
            return "Erro ao consultar entregadores. Tente novamente.";
        }
    }

    private String rastrearEntregadorPorPedido(ConversationSession session, int pedidoId) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT v.id, v.entregador_id, v.status, e.nome as entregador_nome, "
                + "r.latitude, r.longitude, r.data_hora, r.ativo as gps_ativo "
                + "FROM vendas v "
                + "LEFT JOIN entregadores e ON v.entregador_id = e.id "
                + "LEFT JOIN rastreamento_entregador r ON v.entregador_id = r.entregador_id "
                + "WHERE v.id = ?");
            ps.setInt(1, pedidoId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append("\uD83D\uDCCB *RASTREAMENTO - Pedido #").append(pedidoId).append("*\n");
                sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");

                String status = rs.getString("status");
                sb.append("\uD83D\uDCE6 Status: *").append(status != null ? status.toUpperCase() : "N/A").append("*\n\n");

                int entregadorId = rs.getInt("entregador_id");
                if (entregadorId > 0) {
                    String nomeEntregador = rs.getString("entregador_nome");
                    sb.append("\uD83D\uDEB4 Entregador: *").append(nomeEntregador != null ? nomeEntregador : "N/A").append("*\n");

                    double lat = rs.getDouble("latitude");
                    double lng = rs.getDouble("longitude");
                    boolean gpsAtivo = rs.getBoolean("gps_ativo");
                    String dataHora = rs.getString("data_hora");

                    if (gpsAtivo && lat != 0 && lng != 0) {
                        sb.append("\uD83D\uDCCD GPS: *ATIVO*\n");
                        sb.append("\uD83D\uDD50 Atualizado: ").append(FormatUtils.formatDate(dataHora)).append("\n\n");
                        sb.append("\uD83D\uDDFA\uFE0F *Ver no mapa:*\n");
                        sb.append("https://maps.google.com/?q=").append(lat).append(",").append(lng).append("\n");
                    } else if (lat != 0 && lng != 0) {
                        sb.append("\uD83D\uDCCD GPS: INATIVO\n");
                        sb.append("\uD83D\uDD50 Ultima posicao: ").append(FormatUtils.formatDate(dataHora)).append("\n\n");
                        sb.append("\uD83D\uDDFA\uFE0F https://maps.google.com/?q=").append(lat).append(",").append(lng).append("\n");
                    } else {
                        sb.append("\uD83D\uDCCD Sem dados de localizacao\n");
                    }
                } else {
                    sb.append("\uD83D\uDEB4 Nenhum entregador atribuido.\n");
                }

                rs.close();
                ps.close();

                sb.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                sb.append("Digite outro numero de pedido\n");
                sb.append("*99* - Ver entregadores ativos\n");
                sb.append("*0* - Voltar ao menu");
                return sb.toString();
            }

            rs.close();
            ps.close();
            return "Pedido #" + pedidoId + " nao encontrado.\n\nDigite outro numero ou *0* p/ menu.";

        } catch (Exception e) {
            Log.e(TAG, "Erro ao rastrear entregador por pedido", e);
            return "Erro ao rastrear. Tente novamente.";
        }
    }

    private String buscarEntregadorPorNome(ConversationSession session, String nome) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT r.entregador_id, e.nome, r.latitude, r.longitude, r.data_hora, r.ativo "
                + "FROM rastreamento_entregador r "
                + "INNER JOIN entregadores e ON r.entregador_id = e.id "
                + "WHERE e.nome LIKE ? "
                + "ORDER BY r.data_hora DESC LIMIT 5");
            ps.setString(1, "%" + nome + "%");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83D\uDCCD *Resultado:*\n\n");

            int count = 0;
            while (rs.next()) {
                count++;
                String nomeEnt = rs.getString("nome");
                double lat = rs.getDouble("latitude");
                double lng = rs.getDouble("longitude");
                boolean ativo = rs.getBoolean("ativo");
                String dataHora = rs.getString("data_hora");

                sb.append("\uD83D\uDEB4 *").append(nomeEnt).append("*\n");
                sb.append("Status: ").append(ativo ? "ATIVO" : "INATIVO").append("\n");
                if (lat != 0 && lng != 0) {
                    sb.append("\uD83D\uDD50 ").append(FormatUtils.formatDate(dataHora)).append("\n");
                    sb.append("\uD83D\uDDFA\uFE0F https://maps.google.com/?q=").append(lat).append(",").append(lng).append("\n");
                } else {
                    sb.append("Sem localizacao\n");
                }
                sb.append("\n");
            }
            rs.close();
            ps.close();

            if (count == 0) {
                return "Nenhum entregador encontrado.\n\n*99* - Ver ativos\n*0* - Voltar ao menu";
            }

            sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            sb.append("*0* - Voltar ao menu");
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar entregador por nome", e);
            return "Erro na busca. Tente novamente.";
        }
    }

    /**
     * Retorna o logger.
     */
    public WhatsAppBotLogger getLogger() {
        return logger;
    }

    /**
     * Retorna o config.
     */
    public WhatsAppBotConfig getConfig() {
        return config;
    }

    // ===================== v4.6.0 - IMPRESSAO AUTOMATICA DO PEDIDO =====================

    /**
     * v4.6.0 - Imprime automaticamente o cupom do pedido na impressora configurada.
     * Executado em thread separada para nao bloquear o fluxo do bot.
     * Verifica se a impressao automatica esta habilitada nas configuracoes do bot
     * e se a impressora esta configurada no sistema.
     *
     * @param vendaId ID da venda/pedido recem-criado
     */
    private void imprimirPedidoAutomatico(int vendaId) {
        try {
            // Verificar se a impressao automatica esta habilitada
            if (!config.isImpressaoAutoWhatsApp()) {
                Log.d(TAG, "Impressao automatica desabilitada, pulando impressao do pedido #" + vendaId);
                return;
            }

            // Executar impressao em thread separada para nao bloquear o bot
            new Thread(() -> {
                try {
                    PrinterManager printerManager = new PrinterManager(context);

                    // Verificar se a impressora esta configurada
                    if (!printerManager.isImpressoraConfigurada()) {
                        Log.w(TAG, "Impressora nao configurada, nao foi possivel imprimir pedido #" + vendaId);
                        logger.logErro("Impressao automatica falhou: impressora nao configurada", null);
                        return;
                    }

                    // Gerar cupom do pedido
                    CupomGenerator cupomGenerator = new CupomGenerator(context);
                    String cupom = cupomGenerator.gerarCupom(vendaId);

                    if (cupom != null && !cupom.isEmpty()) {
                        // Enviar para impressora
                        printerManager.imprimirTexto(cupom);
                        Log.d(TAG, "Pedido #" + vendaId + " impresso automaticamente com sucesso");
                    } else {
                        Log.w(TAG, "Cupom gerado vazio para pedido #" + vendaId);
                        logger.logErro("Impressao automatica: cupom vazio para pedido #" + vendaId, null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro na impressao automatica do pedido #" + vendaId, e);
                    logger.logErro("Erro na impressao automatica do pedido #" + vendaId, e);
                }
            }, "WhatsBot-AutoPrint-" + vendaId).start();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar impressao automatica", e);
        }
    }
}
