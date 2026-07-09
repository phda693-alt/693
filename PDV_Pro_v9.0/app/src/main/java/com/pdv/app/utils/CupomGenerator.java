package com.pdv.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.*;
import com.pdv.app.utils.LogoManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v7.0.3 - Gerador de cupom nao fiscal com suporte a negrito para armario sauna.
 *
 * Quando a venda for oriunda de armario sauna (uso_armario_id > 0),
 * exibe em negrito no cupom:
 * - Data e hora da ABERTURA do armario (data_entrada do uso_armario_sauna)
 * - Data e hora do FECHAMENTO (data_venda = momento do fechamento da conta)
 *
 * Marcadores de negrito: <b> e </b> sao processados pelo PrinterManager
 * para enviar comandos ESC/POS de negrito (ESC E 1 / ESC E 0).
 */
public class CupomGenerator {
    private static final String TAG = "CupomGenerator";
    private Context context;
    private PrinterManager printerManager;

    public CupomGenerator(Context context) {
        this.context = context;
        this.printerManager = new PrinterManager(context);
    }

    public String gerarCupom(int vendaId) {
        try {
            int colunas = printerManager.getColunasTexto();
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();

            // Get empresa data
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM empresa LIMIT 1");
            String razaoSocial = "", nomeFantasia = "", cnpj = "", endereco = "", telefone = "";
            if (rs.next()) {
                razaoSocial = safeStr(rs.getString("razao_social"));
                nomeFantasia = safeStr(rs.getString("nome_fantasia"));
                cnpj = safeStr(rs.getString("cnpj"));
                endereco = safeStr(rs.getString("endereco")) + ", " + safeStr(rs.getString("numero"))
                        + " - " + safeStr(rs.getString("bairro")) + " - " + safeStr(rs.getString("cidade"))
                        + "/" + safeStr(rs.getString("uf"));
                telefone = safeStr(rs.getString("telefone"));
            }
            rs.close();

            // Get venda data (incluindo campos de entrega e uso_armario_id)
            rs = stmt.executeQuery("SELECT v.*, c.nome as cliente_nome, "
                    + "c.endereco as cli_endereco, c.numero as cli_numero, "
                    + "c.bairro as cli_bairro, c.cidade as cli_cidade, "
                    + "c.uf as cli_uf, c.cep as cli_cep, c.celular as cli_celular "
                    + "FROM vendas v "
                    + "LEFT JOIN clientes c ON v.cliente_id = c.id WHERE v.id = " + vendaId);
            Venda venda = new Venda();
            String clienteNome = "Cliente nao informado";
            boolean isEntrega = false;
            double taxaEntregaValor = 0;
            String bairroEntregaStr = "";
            String enderecoEntregaStr = "";
            // Dados do cliente para o cupom de entrega
            String cliEndereco = "", cliNumero = "", cliBairro = "", cliCidade = "", cliUf = "", cliCep = "", cliCelular = "";
            // v7.0.3 - Dados do armario sauna
            int usoArmarioId = 0;

            if (rs.next()) {
                venda.setId(rs.getInt("id"));
                venda.setDataVenda(rs.getString("data_venda"));
                venda.setTotalBruto(rs.getDouble("total_bruto"));
                venda.setDescontoValor(rs.getDouble("desconto_valor"));
                venda.setAcrescimoValor(rs.getDouble("acrescimo_valor"));
                venda.setTotalLiquido(rs.getDouble("total_liquido"));
                venda.setValorRecebido(rs.getDouble("valor_recebido"));
                venda.setTroco(rs.getDouble("troco"));
                venda.setObservacao(rs.getString("observacao"));
                clienteNome = safeStr(rs.getString("cliente_nome"));
                if (clienteNome.isEmpty()) clienteNome = "Cliente nao informado";

                // Campos de entrega
                try {
                    isEntrega = rs.getInt("para_entrega") == 1;
                    taxaEntregaValor = rs.getDouble("taxa_entrega");
                    bairroEntregaStr = safeStr(rs.getString("bairro_entrega"));
                    enderecoEntregaStr = safeStr(rs.getString("endereco_entrega"));
                } catch (Exception ignored) {}

                // v7.0.3 - Ler uso_armario_id
                try {
                    usoArmarioId = rs.getInt("uso_armario_id");
                } catch (Exception ignored) {}

                // Dados do cliente
                cliEndereco = safeStr(rs.getString("cli_endereco"));
                cliNumero = safeStr(rs.getString("cli_numero"));
                cliBairro = safeStr(rs.getString("cli_bairro"));
                cliCidade = safeStr(rs.getString("cli_cidade"));
                cliUf = safeStr(rs.getString("cli_uf"));
                cliCep = safeStr(rs.getString("cli_cep"));
                cliCelular = safeStr(rs.getString("cli_celular"));
            }
            rs.close();

            // v7.0.3 - Buscar data de abertura do armario sauna se uso_armario_id > 0
            String dataAberturaArmario = "";
            if (usoArmarioId > 0) {
                try {
                    rs = stmt.executeQuery("SELECT data_entrada FROM uso_armario_sauna WHERE id = " + usoArmarioId);
                    if (rs.next()) {
                        String de = rs.getString("data_entrada");
                        if (de != null && !de.isEmpty()) {
                            dataAberturaArmario = FormatUtils.formatDate(de);
                        }
                    }
                    rs.close();
                } catch (Exception ignored) {}
            }

            // v7.0.3 - Fallback: se nao tem uso_armario_id mas observacao contem "Armario Sauna",
            // tentar buscar pela observacao e data_venda
            if (usoArmarioId <= 0 && venda.getObservacao() != null
                    && venda.getObservacao().toLowerCase().contains("armario sauna")) {
                try {
                    // Extrair numero do armario da observacao
                    String obs = venda.getObservacao();
                    String numStr = obs.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        int armarioNumero = Integer.parseInt(numStr);
                        rs = stmt.executeQuery(
                                "SELECT u.id, u.data_entrada FROM uso_armario_sauna u "
                                + "INNER JOIN armarios_sauna a ON u.armario_id = a.id "
                                + "WHERE a.numero = " + armarioNumero
                                + " AND u.status = 'encerrado' "
                                + "ORDER BY u.data_saida DESC LIMIT 1");
                        if (rs.next()) {
                            usoArmarioId = rs.getInt("id");
                            String de = rs.getString("data_entrada");
                            if (de != null && !de.isEmpty()) {
                                dataAberturaArmario = FormatUtils.formatDate(de);
                            }
                        }
                        rs.close();
                    }
                } catch (Exception ignored) {}
            }

            // Get itens
            rs = stmt.executeQuery("SELECT * FROM itens_venda WHERE venda_id = " + vendaId);
            List<ItemVenda> itens = new ArrayList<>();
            List<Integer> itensIds = new ArrayList<>();
            while (rs.next()) {
                ItemVenda item = new ItemVenda();
                item.setId(rs.getInt("id"));
                item.setDescricaoProduto(rs.getString("descricao_produto"));
                item.setQuantidade(rs.getDouble("quantidade"));
                item.setPrecoUnitario(rs.getDouble("preco_unitario"));
                item.setTotal(rs.getDouble("total"));
                itens.add(item);
                itensIds.add(rs.getInt("id"));
            }
            rs.close();

            // v6.3.0 - Carregar adicionais de cada item
            Map<Integer, List<String[]>> adicionaisPorItem = new java.util.LinkedHashMap<>();
            for (int itemId : itensIds) {
                try {
                    rs = stmt.executeQuery("SELECT descricao_adicional, preco FROM itens_venda_adicionais WHERE item_venda_id = " + itemId);
                    List<String[]> adList = new ArrayList<>();
                    while (rs.next()) {
                        adList.add(new String[]{rs.getString("descricao_adicional"), FormatUtils.formatMoney(rs.getDouble("preco"))});
                    }
                    rs.close();
                    if (!adList.isEmpty()) {
                        adicionaisPorItem.put(itemId, adList);
                    }
                } catch (Exception ignored) {}
            }

            // Get pagamentos
            rs = stmt.executeQuery("SELECT pv.*, fp.descricao as forma_desc FROM pagamentos_venda pv "
                    + "LEFT JOIN formas_pagamento fp ON pv.forma_pagamento_id = fp.id WHERE pv.venda_id = " + vendaId);
            List<PagamentoVenda> pagamentos = new ArrayList<>();
            while (rs.next()) {
                PagamentoVenda pag = new PagamentoVenda();
                pag.setFormaDescricao(rs.getString("forma_desc"));
                pag.setValor(rs.getDouble("valor"));
                pag.setParcelas(rs.getInt("parcelas"));
                pagamentos.add(pag);
            }
            rs.close();

            // Get observacao cupom
            rs = stmt.executeQuery("SELECT texto FROM observacoes_cupom WHERE ativo = 1 LIMIT 1");
            String obsCupom = "";
            if (rs.next()) {
                obsCupom = safeStr(rs.getString("texto"));
            }
            rs.close();
            stmt.close();

            // Build cupom
            StringBuilder sb = new StringBuilder();
            String line = repeat("-", colunas);

            // Logo do cupom - marcador especial para o PrinterManager processar
            Bitmap logoBitmap = LogoManager.carregarLogo(context);
            if (logoBitmap != null) {
                sb.append("[LOGO_CUPOM]\n");
            }

            sb.append(center(nomeFantasia.isEmpty() ? razaoSocial : nomeFantasia, colunas)).append("\n");
            if (!razaoSocial.isEmpty() && !nomeFantasia.isEmpty()) {
                sb.append(center(razaoSocial, colunas)).append("\n");
            }
            if (!cnpj.isEmpty()) sb.append(center("CNPJ: " + cnpj, colunas)).append("\n");
            if (!endereco.isEmpty()) sb.append(center(endereco, colunas)).append("\n");
            if (!telefone.isEmpty()) sb.append(center("Tel: " + telefone, colunas)).append("\n");
            sb.append(line).append("\n");

            // Se for entrega, indicar no cupom
            if (isEntrega) {
                sb.append(center("*** PEDIDO PARA ENTREGA ***", colunas)).append("\n");
                sb.append(line).append("\n");
            }

            sb.append(center("CUPOM NAO FISCAL", colunas)).append("\n");
            sb.append("Venda: #" + vendaId).append("\n");
            sb.append("Data: " + FormatUtils.formatDate(venda.getDataVenda())).append("\n");
            sb.append("Cliente: " + clienteNome).append("\n");

            // v7.0.3 - Se for armario sauna, exibir data/hora de abertura e fechamento em NEGRITO
            if (usoArmarioId > 0) {
                sb.append(line).append("\n");
                if (!dataAberturaArmario.isEmpty()) {
                    sb.append("<b>ABERTURA: " + dataAberturaArmario + "</b>").append("\n");
                }
                // Fechamento = data_venda (momento do fechamento da conta)
                String dataFechamento = FormatUtils.formatDate(venda.getDataVenda());
                if (!dataFechamento.isEmpty()) {
                    sb.append("<b>FECHAMENTO: " + dataFechamento + "</b>").append("\n");
                }
            }

            // Se for entrega, mostrar dados de endereco do cliente
            if (isEntrega) {
                sb.append(line).append("\n");
                sb.append(center("DADOS DE ENTREGA", colunas)).append("\n");
                sb.append(line).append("\n");

                // Endereco completo do cliente
                if (!cliEndereco.isEmpty()) {
                    String endCompleto = cliEndereco;
                    if (!cliNumero.isEmpty()) endCompleto += ", " + cliNumero;
                    sb.append("Endereco: " + endCompleto).append("\n");
                }
                if (!cliBairro.isEmpty()) {
                    sb.append("Bairro: " + cliBairro).append("\n");
                }
                if (!cliCidade.isEmpty() || !cliUf.isEmpty()) {
                    String cidadeUf = cliCidade;
                    if (!cliUf.isEmpty()) cidadeUf += "/" + cliUf;
                    sb.append("Cidade: " + cidadeUf).append("\n");
                }
                if (!cliCep.isEmpty()) {
                    sb.append("CEP: " + cliCep).append("\n");
                }
                if (!cliCelular.isEmpty()) {
                    sb.append("Telefone: " + cliCelular).append("\n");
                }

                // Bairro de entrega (da taxa)
                if (!bairroEntregaStr.isEmpty()) {
                    sb.append("Bairro Entrega: " + bairroEntregaStr).append("\n");
                }
                // Endereco de entrega (se diferente)
                if (!enderecoEntregaStr.isEmpty() && !enderecoEntregaStr.equals(cliEndereco)) {
                    sb.append("End. Entrega: " + enderecoEntregaStr).append("\n");
                }

                sb.append("Taxa Entrega: R$ " + FormatUtils.formatMoney(taxaEntregaValor)).append("\n");
            }

            sb.append(line).append("\n");

            // Header items
            sb.append("ITEM  DESCRICAO          QTD    TOTAL\n");
            sb.append(line).append("\n");

            int itemNum = 1;
            for (ItemVenda item : itens) {
                String desc = item.getDescricaoProduto();
                // v6.3.0 - Remover adicionais da descricao para exibir separado
                String descLimpa = desc;
                if (descLimpa != null && descLimpa.contains(" [")) {
                    descLimpa = descLimpa.substring(0, descLimpa.indexOf(" ["));
                }
                if (descLimpa != null && descLimpa.length() > 18) descLimpa = descLimpa.substring(0, 18);
                sb.append(String.format("%-5d %-18s %5s %8s\n",
                        itemNum++,
                        descLimpa != null ? descLimpa : "",
                        FormatUtils.formatQuantidade(item.getQuantidade()),
                        FormatUtils.formatMoney(item.getTotal())));
                // v6.3.0 - Exibir adicionais abaixo do item
                List<String[]> adicionaisItem = adicionaisPorItem.get(item.getId());
                if (adicionaisItem != null && !adicionaisItem.isEmpty()) {
                    for (String[] ad : adicionaisItem) {
                        String adDesc = ad[0];
                        String adPreco = ad[1];
                        sb.append("      + ").append(adDesc != null ? adDesc : "");
                        if (adPreco != null && !adPreco.equals("0,00")) {
                            sb.append(" R$ ").append(adPreco);
                        }
                        sb.append("\n");
                    }
                }
            }

            sb.append(line).append("\n");

            // === RESUMO DA VENDA (ordem: Total, Desconto, Acrescimo, Taxa Entrega, Subtotal) ===

            // 1. TOTAL (valor final da venda)
            sb.append(rightAlign("TOTAL: R$ " + FormatUtils.formatMoney(venda.getTotalLiquido()), colunas)).append("\n");

            // 2. DESCONTO (se houver)
            if (venda.getDescontoValor() > 0) {
                sb.append(rightAlign("DESCONTO: -R$ " + FormatUtils.formatMoney(venda.getDescontoValor()), colunas)).append("\n");
            }

            // 3. ACRESCIMO (se houver)
            if (venda.getAcrescimoValor() > 0) {
                sb.append(rightAlign("ACRESCIMO: +R$ " + FormatUtils.formatMoney(venda.getAcrescimoValor()), colunas)).append("\n");
            }

            // Taxa de entrega (se houver)
            if (isEntrega && taxaEntregaValor > 0) {
                sb.append(rightAlign("TAXA ENTREGA: +R$ " + FormatUtils.formatMoney(taxaEntregaValor), colunas)).append("\n");
            }

            // 4. SUBTOTAL (soma dos itens antes de desconto/acrescimo)
            sb.append(rightAlign("SUBTOTAL: R$ " + FormatUtils.formatMoney(venda.getTotalBruto()), colunas)).append("\n");

            sb.append(line).append("\n");

            // 5. VALOR RECEBIDO
            if (venda.getValorRecebido() > 0) {
                sb.append(rightAlign("VALOR RECEBIDO: R$ " + FormatUtils.formatMoney(venda.getValorRecebido()), colunas)).append("\n");
            }

            // 6. TROCO
            if (venda.getTroco() > 0) {
                sb.append(rightAlign("TROCO: R$ " + FormatUtils.formatMoney(venda.getTroco()), colunas)).append("\n");
            }

            sb.append(line).append("\n");

            // 7. FORMAS DE PAGAMENTO (cada forma com seu respectivo valor)
            sb.append("FORMAS DE PAGAMENTO:\n");
            for (PagamentoVenda pag : pagamentos) {
                String forma = pag.getFormaDescricao() != null ? pag.getFormaDescricao() : "N/A";
                String parcela = pag.getParcelas() > 1 ? " (" + pag.getParcelas() + "x)" : "";
                sb.append("  " + forma + parcela + ": R$ " + FormatUtils.formatMoney(pag.getValor())).append("\n");
            }

            sb.append(line).append("\n");

            // 8. OBSERVACAO DA VENDA (se houver)
            if (venda.getObservacao() != null && !venda.getObservacao().isEmpty()) {
                sb.append("OBSERVACAO:\n");
                sb.append("  " + venda.getObservacao()).append("\n");
                sb.append(line).append("\n");
            }

            // Verificar se tem pagamento do tipo Contas a Receber
            boolean temContaReceber = false;
            double valorContaReceber = 0;
            for (PagamentoVenda pag : pagamentos) {
                if (pag.getFormaDescricao() != null && pag.getFormaDescricao().toLowerCase().contains("conta")) {
                    temContaReceber = true;
                    valorContaReceber += pag.getValor();
                }
            }
            if (temContaReceber) {
                sb.append(center("*** CONTA A RECEBER ***", colunas)).append("\n");
                sb.append(center("Valor: R$ " + FormatUtils.formatMoney(valorContaReceber), colunas)).append("\n");
                sb.append(center("Cliente: " + clienteNome, colunas)).append("\n");
                sb.append(line).append("\n");
            }

            if (!obsCupom.isEmpty()) {
                sb.append(center(obsCupom, colunas)).append("\n");
                sb.append(line).append("\n");
            }

            sb.append(center("PDV Pro v8.0.0", colunas)).append("\n");
            sb.append(center("Obrigado pela preferencia!", colunas)).append("\n");
            sb.append(center("phdatech (85) 98123-7727", colunas)).append("\n");

            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar cupom", e);
            return "Erro ao gerar cupom: " + e.getMessage();
        }
    }


    /**
     * v8.0.12.5 - Gera o cupom com a opcao de mostrar a senha no proprio cupom.
     * Mantem o gerarCupom(vendaId) original para compatibilidade com o resto do sistema.
     */
    public String gerarCupom(int vendaId, boolean exibirSenhaNoCupom) {
        String cupom = gerarCupom(vendaId);
        if (!exibirSenhaNoCupom || cupom == null || cupom.startsWith("Erro ao gerar cupom")) {
            return cupom;
        }

        int colunas = printerManager.getColunasTexto();
        String line = repeat("-", colunas);
        String senha = formatarSenha(vendaId);
        String blocoSenha = line + "\n"
                + "<center><b>SENHA DO PEDIDO</b></center>" + "\n"
                + "<senha20>" + senha + "</senha20>"
                + line + "\n";

        String marcador = "ITEM  DESCRICAO";
        int idx = cupom.indexOf(marcador);
        if (idx >= 0) {
            return cupom.substring(0, idx) + blocoSenha + cupom.substring(idx);
        }

        String rodape = "PDV Pro v8.0.0";
        idx = cupom.indexOf(rodape);
        if (idx >= 0) {
            return cupom.substring(0, idx) + blocoSenha + cupom.substring(idx);
        }

        return cupom + "\n" + blocoSenha;
    }


    /**
     * v8.0.12.2 - Gera um canhoto simples de senha para retirada/atendimento.
     * Usa o ID da venda como senha para evitar duplicidade e facilitar chamada no painel.
     */
    public String gerarCanhotoSenha(int vendaId) {
        try {
            int colunas = printerManager.getColunasTexto();
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();

            Statement stmt = conn.createStatement();

            String nomeFantasia = "";
            String razaoSocial = "";
            ResultSet rs = stmt.executeQuery("SELECT nome_fantasia, razao_social FROM empresa LIMIT 1");
            if (rs.next()) {
                nomeFantasia = safeStr(rs.getString("nome_fantasia"));
                razaoSocial = safeStr(rs.getString("razao_social"));
            }
            rs.close();

            String dataVenda = "";
            String clienteNome = "Cliente nao informado";
            double totalLiquido = 0;
            rs = stmt.executeQuery("SELECT v.data_venda, v.total_liquido, c.nome AS cliente_nome "
                    + "FROM vendas v LEFT JOIN clientes c ON v.cliente_id = c.id WHERE v.id = " + vendaId);
            if (rs.next()) {
                dataVenda = safeStr(rs.getString("data_venda"));
                clienteNome = safeStr(rs.getString("cliente_nome"));
                totalLiquido = rs.getDouble("total_liquido");
            }
            rs.close();
            stmt.close();

            if (clienteNome.isEmpty()) clienteNome = "Cliente nao informado";

            String senha = formatarSenha(vendaId);
            String empresa = !nomeFantasia.isEmpty() ? nomeFantasia : razaoSocial;

            // v8.0.12.9 - Todas as impressoes do sistema de senha em layout comercial compacto.
            // Sem margem superior/inferior, mantendo tracejado, organizacao e visual de cupom.
            String line = repeat("-", colunas);
            StringBuilder sb = new StringBuilder();
            sb.append("<sem_margem>");
            if (!empresa.isEmpty()) sb.append(center(empresa, colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append(center("CANHOTO DE SENHA", colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append(center("SENHA", colunas)).append("\n");
            sb.append(gerarSenhaFonte20Centralizada(senha));
            sb.append(line).append("\n");
            String vendaTotal = "Venda:#" + vendaId + "  Total:R$" + FormatUtils.formatMoney(totalLiquido);
            sb.append(compactarLinhaCanhoto(vendaTotal, colunas)).append("\n");
            if (!dataVenda.isEmpty()) {
                String dataLinha = "Data:" + FormatUtils.formatDate(dataVenda);
                sb.append(compactarLinhaCanhoto(dataLinha, colunas)).append("\n");
            }
            sb.append("Cliente:").append(compactarClienteCanhoto(clienteNome, colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append(center("AGUARDE SER CHAMADO", colunas));
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar canhoto de senha", e);
            return "<sem_margem>CANHOTO DE SENHA\nVenda:#" + vendaId;
        }
    }

    private String compactarLinhaCanhoto(String texto, int colunas) {
        if (texto == null) return "";
        String linha = texto.trim().replaceAll("\\s+", " ");
        if (linha.length() > colunas) linha = linha.substring(0, colunas);
        return linha;
    }

    private String compactarClienteCanhoto(String clienteNome, int colunas) {
        if (clienteNome == null || clienteNome.trim().isEmpty()) return "Cliente nao informado";
        String cliente = clienteNome.trim().replaceAll("\\s+", " ");
        int limite = Math.max(12, colunas - 8);
        if (cliente.length() > limite) cliente = cliente.substring(0, limite);
        return cliente;
    }

    private String formatarSenha(int vendaId) {
        int senhaAtendimento = SenhaChamadoStore.obterOuCriarSenhaDaVenda(context, vendaId);
        return SenhaChamadoStore.formatarSenhaNumero(senhaAtendimento);
    }

    private String gerarSenhaFonte20Centralizada(String senha) {
        if (senha == null) senha = "";
        // IMPORTANTE:
        // <senha20> NAO e texto para imprimir. E um marcador interno do sistema.
        // O numero 20 representa o tamanho solicitado da fonte da senha no canhoto.
        // O PrinterManager interpreta esse marcador e envia os comandos ESC/POS de:
        // centralizacao real + negrito real + maior ampliacao de fonte compativel.
        // Em rotas texto puro/print server, o PrinterManager substitui o marcador
        // por uma linha centralizada manualmente para evitar sair grudado na esquerda.
        return "<senha20>" + senha + "</senha20>";
    }


    private String gerarPayloadQrCanhotoSenha(int vendaId, String senha, String dataVenda, String clienteNome, double totalLiquido) {
        StringBuilder qr = new StringBuilder();
        qr.append("PDV_PRO_CANHOTO");
        qr.append("|SENHA=").append(safeQr(senha));
        qr.append("|VENDA=").append(vendaId);
        if (dataVenda != null && !dataVenda.isEmpty()) {
            qr.append("|DATA=").append(safeQr(FormatUtils.formatDate(dataVenda)));
        }
        qr.append("|CLIENTE=").append(safeQr(clienteNome));
        qr.append("|TOTAL=R$").append(safeQr(FormatUtils.formatMoney(totalLiquido)));
        return qr.toString();
    }

    private String safeQr(String s) {
        if (s == null) return "";
        return s.replace("|", " ")
                .replace("<", " ")
                .replace(">", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();
    }

    private String safeStr(String s) { return s != null ? s : ""; }

    private String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        int pad = (width - text.length()) / 2;
        return repeat(" ", pad) + text;
    }

    private String rightAlign(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text;
        return repeat(" ", width - text.length()) + text;
    }

    private String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
