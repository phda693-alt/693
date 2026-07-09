package com.pdv.app.models;

/**
 * Modelo que representa uma entrega no sistema PDV Pro.
 * Combina dados da venda com informacoes de entrega e rastreamento.
 */
public class Entrega {
    private int vendaId;
    private int clienteId;
    private int entregadorId;
    private String clienteNome;
    private String clienteCelular;
    private String clienteEndereco;
    private String entregadorNome;
    private String entregadorCelular;
    private String dataVenda;
    private double totalLiquido;
    private String status; // pendente, em_rota, entregue, cancelada
    private String observacao;
    private boolean origemWhatsApp;
    private String contatoWhatsApp;

    // Dados de rastreamento
    private double latitude;
    private double longitude;
    private String ultimaPosicao;
    private boolean gpsAtivo;

    public Entrega() {}

    // Getters e Setters
    public int getVendaId() { return vendaId; }
    public void setVendaId(int vendaId) { this.vendaId = vendaId; }

    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }

    public int getEntregadorId() { return entregadorId; }
    public void setEntregadorId(int entregadorId) { this.entregadorId = entregadorId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public String getClienteCelular() { return clienteCelular; }
    public void setClienteCelular(String clienteCelular) { this.clienteCelular = clienteCelular; }

    public String getClienteEndereco() { return clienteEndereco; }
    public void setClienteEndereco(String clienteEndereco) { this.clienteEndereco = clienteEndereco; }

    public String getEntregadorNome() { return entregadorNome; }
    public void setEntregadorNome(String entregadorNome) { this.entregadorNome = entregadorNome; }

    public String getEntregadorCelular() { return entregadorCelular; }
    public void setEntregadorCelular(String entregadorCelular) { this.entregadorCelular = entregadorCelular; }

    public String getDataVenda() { return dataVenda; }
    public void setDataVenda(String dataVenda) { this.dataVenda = dataVenda; }

    public double getTotalLiquido() { return totalLiquido; }
    public void setTotalLiquido(double totalLiquido) { this.totalLiquido = totalLiquido; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public boolean isOrigemWhatsApp() { return origemWhatsApp; }
    public void setOrigemWhatsApp(boolean origemWhatsApp) { this.origemWhatsApp = origemWhatsApp; }

    public String getContatoWhatsApp() { return contatoWhatsApp; }
    public void setContatoWhatsApp(String contatoWhatsApp) { this.contatoWhatsApp = contatoWhatsApp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getUltimaPosicao() { return ultimaPosicao; }
    public void setUltimaPosicao(String ultimaPosicao) { this.ultimaPosicao = ultimaPosicao; }

    public boolean isGpsAtivo() { return gpsAtivo; }
    public void setGpsAtivo(boolean gpsAtivo) { this.gpsAtivo = gpsAtivo; }

    /**
     * Retorna o status formatado para exibicao.
     */
    public String getStatusFormatado() {
        if (status == null) return "N/A";
        switch (status) {
            case "pendente": return "PENDENTE";
            case "em_rota": return "EM ROTA";
            case "entregue": return "ENTREGUE";
            case "cancelada": return "CANCELADA";
            case "finalizada": return "FINALIZADA";
            default: return status.toUpperCase();
        }
    }

    /**
     * Extrai o contato WhatsApp da observacao da venda.
     */
    public String extrairContatoWhatsApp() {
        if (observacao != null && observacao.contains("Contato:")) {
            String contato = observacao.substring(observacao.indexOf("Contato:") + 8).trim();
            // Limpar possíveis caracteres extras
            if (contato.contains("\n")) {
                contato = contato.substring(0, contato.indexOf("\n")).trim();
            }
            return contato;
        }
        return null;
    }
}
