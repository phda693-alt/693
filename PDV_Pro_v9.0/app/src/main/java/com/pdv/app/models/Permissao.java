package com.pdv.app.models;

/**
 * Modelo que representa uma Permissao individual no sistema.
 * Cada permissao define uma acao especifica dentro de um modulo.
 *
 * Estrutura: modulo + acao + chave unica
 * Exemplo: modulo="vendas", acao="criar", chave="vendas.criar"
 */
public class Permissao {
    private int id;
    private String modulo;       // Ex: "vendas", "caixa", "produtos", "relatorios"
    private String acao;         // Ex: "acessar", "criar", "editar", "excluir", "cancelar"
    private String chave;        // Ex: "vendas.criar" - identificador unico
    private String descricao;    // Descricao legivel da permissao
    private boolean concedida;   // Flag auxiliar (nao persistido na tabela permissoes)

    public Permissao() {}

    public Permissao(String modulo, String acao, String chave, String descricao) {
        this.modulo = modulo;
        this.acao = acao;
        this.chave = chave;
        this.descricao = descricao;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }

    public String getAcao() { return acao; }
    public void setAcao(String acao) { this.acao = acao; }

    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public boolean isConcedida() { return concedida; }
    public void setConcedida(boolean concedida) { this.concedida = concedida; }

    @Override
    public String toString() { return descricao != null ? descricao : chave; }
}
