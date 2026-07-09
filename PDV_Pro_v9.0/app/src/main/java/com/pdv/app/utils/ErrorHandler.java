package com.pdv.app.utils;

import android.content.Context;
import android.util.Log;

/**
 * Classe utilitaria centralizada para tratamento de erros.
 * Converte mensagens de erro tecnicas em mensagens amigaveis
 * para o usuario final do PDV Pro.
 *
 * @version 3.2
 */
public class ErrorHandler {
    private static final String TAG = "ErrorHandler";

    // ========== CATEGORIAS DE CONTEXTO ==========

    /** Contexto: carregando dados de uma listagem */
    public static final String CTX_CARREGAR = "carregar";
    /** Contexto: salvando um registro */
    public static final String CTX_SALVAR = "salvar";
    /** Contexto: excluindo um registro */
    public static final String CTX_EXCLUIR = "excluir";
    /** Contexto: realizando login */
    public static final String CTX_LOGIN = "login";
    /** Contexto: operacao de caixa */
    public static final String CTX_CAIXA = "caixa";
    /** Contexto: operacao de venda */
    public static final String CTX_VENDA = "venda";
    /** Contexto: operacao de pagamento */
    public static final String CTX_PAGAMENTO = "pagamento";
    /** Contexto: operacao de backup */
    public static final String CTX_BACKUP = "backup";
    /** Contexto: operacao de impressao */
    public static final String CTX_IMPRESSAO = "impressao";
    /** Contexto: operacao de licenca */
    public static final String CTX_LICENCA = "licenca";
    /** Contexto: operacao de relatorio */
    public static final String CTX_RELATORIO = "relatorio";
    /** Contexto: operacao de entrega */
    public static final String CTX_ENTREGA = "entrega";
    /** Contexto: operacao de contas a receber */
    public static final String CTX_CONTAS_RECEBER = "contas_receber";
    /** Contexto: operacao generica */
    public static final String CTX_GENERICO = "generico";

    /**
     * Converte uma excecao em uma mensagem amigavel para o usuario,
     * levando em conta o contexto da operacao.
     *
     * @param e       A excecao capturada
     * @param contexto O contexto da operacao (use as constantes CTX_*)
     * @return Mensagem amigavel para exibir ao usuario
     */
    public static String getMensagemAmigavel(Exception e, String contexto) {
        if (e == null) {
            return getMensagemGenerica(contexto);
        }

        String msg = e.getMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        String lower = msg.toLowerCase();

        Log.e(TAG, "Erro [" + contexto + "]: " + msg, e);

        // ===== ERROS DE CONEXAO / REDE =====
        if (lower.contains("communications link failure") || lower.contains("connection reset")
                || lower.contains("broken pipe") || lower.contains("connection abort")) {
            return "A conexao com o servidor foi perdida.\n\n"
                    + "Verifique se sua internet esta funcionando e tente novamente.\n\n"
                    + "Dica: Se o problema persistir, reinicie o aplicativo.";
        }

        if (lower.contains("connection refused")) {
            return "Nao foi possivel conectar ao servidor.\n\n"
                    + "O servidor pode estar desligado ou as configuracoes do banco de dados estao incorretas.\n\n"
                    + "Verifique as configuracoes em Menu > Banco de Dados.";
        }

        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "O servidor demorou muito para responder.\n\n"
                    + "Sua conexao pode estar lenta. Tente novamente em alguns instantes.";
        }

        if (lower.contains("unknown host") || lower.contains("no address associated")
                || lower.contains("unable to resolve host")) {
            return "Servidor nao encontrado.\n\n"
                    + "Verifique o endereco do servidor nas configuracoes do banco de dados "
                    + "e certifique-se de que esta conectado a internet.";
        }

        if (lower.contains("network is unreachable") || lower.contains("no route to host")) {
            return "Sem acesso a rede.\n\n"
                    + "Verifique se o Wi-Fi ou dados moveis estao ativados e tente novamente.";
        }

        // ===== ERROS DE AUTENTICACAO DO BANCO =====
        if (lower.contains("access denied")) {
            return "Acesso negado ao banco de dados.\n\n"
                    + "O usuario ou senha do banco de dados estao incorretos.\n\n"
                    + "Verifique as configuracoes em Menu > Banco de Dados.";
        }

        // ===== ERROS DE CONEXAO EXPIRADA =====
        if (lower.contains("no operations allowed after connection closed")
                || lower.contains("connection is closed")
                || lower.contains("already been closed")) {
            return "A conexao com o servidor expirou.\n\n"
                    + "Tente realizar a operacao novamente. O sistema ira reconectar automaticamente.";
        }

        // ===== ERROS DE SQL / BANCO =====
        if (lower.contains("duplicate entry") || lower.contains("unique constraint")) {
            return "Este registro ja existe no sistema.\n\n"
                    + "Verifique se os dados informados nao estao duplicados.";
        }

        if (lower.contains("cannot be null") || lower.contains("column") && lower.contains("cannot be null")) {
            return "Existem campos obrigatorios que nao foram preenchidos.\n\n"
                    + "Por favor, preencha todos os campos necessarios e tente novamente.";
        }

        if (lower.contains("foreign key constraint") || lower.contains("a foreign key constraint fails")) {
            return "Este registro esta vinculado a outros dados do sistema e nao pode ser alterado desta forma.\n\n"
                    + "Verifique se nao existem vendas ou movimentacoes associadas.";
        }

        if (lower.contains("data too long") || lower.contains("data truncation")) {
            return "Um dos campos possui mais caracteres do que o permitido.\n\n"
                    + "Reduza o tamanho do texto informado e tente novamente.";
        }

        if (lower.contains("table") && lower.contains("doesn't exist")) {
            return "Erro na estrutura do banco de dados.\n\n"
                    + "Uma tabela necessaria nao foi encontrada. "
                    + "Verifique se o banco de dados esta configurado corretamente.";
        }

        if (lower.contains("unknown column")) {
            return "O banco de dados precisa de uma atualizacao de estrutura.\n\n"
                    + "Reinicie o aplicativo para executar a verificacao automatica. "
                    + "Se o aviso continuar, use Diagnostico do Sistema.";
        }

        if (e instanceof NullPointerException || lower.contains("null object reference")
                || lower.contains("intvalue()")) {
            return "Um dado necessario nao foi encontrado para concluir esta operacao.\n\n"
                    + "Atualize a lista e tente novamente. Nenhuma alteracao incompleta foi confirmada.";
        }

        if (lower.contains("malformed") || lower.contains("incorrect string value")
                || lower.contains("character set") || lower.contains("encoding")) {
            return "Um texto contem caracteres que ainda nao puderam ser processados.\n\n"
                    + "Reinicie o aplicativo para atualizar a codificacao do banco e tente novamente.";
        }

        if (lower.contains("no space left") || lower.contains("disk full")) {
            return "Nao ha espaco livre suficiente para concluir a operacao.\n\n"
                    + "Libere espaco no aparelho ou servidor e tente novamente.";
        }

        if (lower.contains("ssl") || lower.contains("certificate")) {
            return "A conexao segura com o servidor nao pode ser validada.\n\n"
                    + "Confira data, hora e configuracoes de rede do aparelho.";
        }

        // ===== ERROS DE DRIVER =====
        if (lower.contains("driver") && lower.contains("not found")) {
            return "Erro interno do aplicativo.\n\n"
                    + "O driver de conexao com o banco de dados nao foi encontrado. "
                    + "Reinstale o aplicativo para corrigir.";
        }

        // ===== ERROS DE FORMATO / CONVERSAO =====
        if (e instanceof NumberFormatException || lower.contains("numberformatexception")
                || lower.contains("for input string")) {
            return "Valor numerico invalido.\n\n"
                    + "Verifique se os campos numericos (preco, quantidade, etc.) "
                    + "estao preenchidos corretamente.\n\n"
                    + "Use apenas numeros e ponto decimal (ex: 10.50).";
        }

        if (lower.contains("parse") || lower.contains("unparseable")) {
            return "Formato de dados invalido.\n\n"
                    + "Verifique se as datas e valores estao no formato correto.";
        }

        // ===== ERROS DE MEMORIA =====
        if (lower.contains("out of memory") || lower.contains("outofmemoryerror")) {
            return "O aplicativo ficou sem memoria disponivel.\n\n"
                    + "Feche outros aplicativos e tente novamente. "
                    + "Se o problema persistir, reinicie o dispositivo.";
        }

        // ===== ERROS DE PERMISSAO =====
        if (lower.contains("permission denied") || lower.contains("securityexception")) {
            return "O aplicativo nao tem permissao para realizar esta operacao.\n\n"
                    + "Verifique as permissoes do aplicativo nas configuracoes do Android.";
        }

        // ===== ERROS DE IMPRESSAO =====
        if (lower.contains("socket") && (lower.contains("connect") || lower.contains("refused"))) {
            if (CTX_IMPRESSAO.equals(contexto)) {
                return "Nao foi possivel conectar a impressora.\n\n"
                        + "Verifique se a impressora esta ligada e conectada a rede.\n\n"
                        + "Confira o endereco IP e a porta nas configuracoes.";
            }
        }

        // ===== ERROS DE SMB/CIFS =====
        if (lower.contains("smbauthexception") || lower.contains("logon failure")) {
            return "Erro de autenticacao na rede.\n\n"
                    + "Verifique o usuario e senha de acesso ao compartilhamento de rede.";
        }

        if (lower.contains("smbexception") || lower.contains("smb")) {
            return "Erro ao acessar o compartilhamento de rede.\n\n"
                    + "Verifique se o computador esta ligado e o compartilhamento esta acessivel.\n\n"
                    + "Confira as configuracoes de rede (host, compartilhamento, usuario e senha).";
        }

        // ===== ERROS DE FTP (BACKUP) =====
        if (lower.contains("ftp") || (CTX_BACKUP.equals(contexto) && lower.contains("connect"))) {
            return "Erro na conexao com o servidor de backup (FTP).\n\n"
                    + "Verifique as configuracoes do servidor FTP:\n"
                    + "- Endereco do servidor\n"
                    + "- Usuario e senha\n"
                    + "- Conexao com a internet";
        }

        // ===== MENSAGEM CONTEXTUAL PADRAO =====
        return getMensagemContextual(contexto, msg);
    }

    /**
     * Retorna uma mensagem generica baseada no contexto quando nao ha excecao.
     */
    private static String getMensagemGenerica(String contexto) {
        switch (contexto) {
            case CTX_CARREGAR:
                return "Nao foi possivel carregar os dados.\n\nVerifique sua conexao e tente novamente.";
            case CTX_SALVAR:
                return "Nao foi possivel salvar o registro.\n\nVerifique os dados informados e tente novamente.";
            case CTX_EXCLUIR:
                return "Nao foi possivel excluir o registro.\n\nTente novamente em alguns instantes.";
            case CTX_LOGIN:
                return "Nao foi possivel realizar o login.\n\nVerifique sua conexao com o servidor.";
            case CTX_CAIXA:
                return "Erro na operacao de caixa.\n\nTente novamente.";
            case CTX_VENDA:
                return "Erro ao processar a venda.\n\nVerifique os dados e tente novamente.";
            case CTX_PAGAMENTO:
                return "Erro ao processar o pagamento.\n\nVerifique os valores e tente novamente.";
            case CTX_BACKUP:
                return "Erro na operacao de backup.\n\nVerifique as configuracoes do servidor FTP.";
            case CTX_IMPRESSAO:
                return "Erro ao imprimir.\n\nVerifique se a impressora esta configurada e conectada.";
            case CTX_LICENCA:
                return "Erro na verificacao da licenca.\n\nTente novamente.";
            case CTX_RELATORIO:
                return "Erro ao gerar o relatorio.\n\nVerifique sua conexao e tente novamente.";
            default:
                return "Ocorreu um erro inesperado.\n\nTente novamente. Se o problema persistir, reinicie o aplicativo.";
        }
    }

    /**
     * Retorna uma mensagem contextual com detalhes tecnicos resumidos.
     */
    private static String getMensagemContextual(String contexto, String detalhe) {
        String prefixo;
        switch (contexto) {
            case CTX_CARREGAR:
                prefixo = "Nao foi possivel carregar os dados.";
                break;
            case CTX_SALVAR:
                prefixo = "Nao foi possivel salvar o registro.";
                break;
            case CTX_EXCLUIR:
                prefixo = "Nao foi possivel excluir o registro.";
                break;
            case CTX_LOGIN:
                prefixo = "Falha ao realizar o login.";
                break;
            case CTX_CAIXA:
                prefixo = "Erro na operacao de caixa.";
                break;
            case CTX_VENDA:
                prefixo = "Erro ao processar a venda.";
                break;
            case CTX_PAGAMENTO:
                prefixo = "Erro ao processar o pagamento.";
                break;
            case CTX_BACKUP:
                prefixo = "Erro na operacao de backup.";
                break;
            case CTX_IMPRESSAO:
                prefixo = "Erro ao imprimir.";
                break;
            case CTX_LICENCA:
                prefixo = "Erro na operacao de licenca.";
                break;
            case CTX_RELATORIO:
                prefixo = "Erro ao gerar o relatorio.";
                break;
            default:
                prefixo = "Ocorreu um erro inesperado.";
                break;
        }

        return prefixo + "\n\n"
                + "Tente novamente. Se o problema persistir, use Diagnostico do Sistema para gerar um resumo tecnico.";
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro de carregamento.
     */
    public static String erroCarregar(Exception e) {
        return getMensagemAmigavel(e, CTX_CARREGAR);
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro ao salvar.
     */
    public static String erroSalvar(Exception e) {
        return getMensagemAmigavel(e, CTX_SALVAR);
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro ao excluir.
     */
    public static String erroExcluir(Exception e) {
        return getMensagemAmigavel(e, CTX_EXCLUIR);
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro de login.
     */
    public static String erroLogin(Exception e) {
        return getMensagemAmigavel(e, CTX_LOGIN);
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro de caixa.
     */
    public static String erroCaixa(Exception e) {
        return getMensagemAmigavel(e, CTX_CAIXA);
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro de venda.
     */
    public static String erroVenda(Exception e) {
        return getMensagemAmigavel(e, CTX_VENDA);
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro de pagamento.
     */
    public static String erroPagamento(Exception e) {
        return getMensagemAmigavel(e, CTX_PAGAMENTO);
    }

    /**
     * Metodo de conveniencia para obter mensagem amigavel de erro de relatorio.
     */
    public static String erroRelatorio(Exception e) {
        return getMensagemAmigavel(e, CTX_RELATORIO);
    }
}
