package com.pdv.app.models;

public class FormaPagamento {
    private int id;
    private String descricao, tipo;
    private boolean permiteParcelamento, exigeCliente, ativo;

    public FormaPagamento() { this.ativo = true; this.tipo = "dinheiro"; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String d) { this.descricao = d; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public boolean isPermiteParcelamento() { return permiteParcelamento; }
    public void setPermiteParcelamento(boolean p) { this.permiteParcelamento = p; }
    public boolean isExigeCliente() { return exigeCliente; }
    public void setExigeCliente(boolean e) { this.exigeCliente = e; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    /**
     * Verifica se esta forma de pagamento e do tipo Contas a Receber.
     */
    public boolean isContaReceber() { return "conta_receber".equals(tipo); }

    @Override
    public String toString() { return descricao != null ? descricao : ""; }
}
