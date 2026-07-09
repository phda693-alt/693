package com.pdv.app.models;

/**
 * Modelo que representa um Perfil de acesso no sistema.
 * Cada perfil agrupa um conjunto de permissoes que definem
 * o que o usuario pode ou nao fazer no sistema.
 *
 * Exemplos: Administrador, Gerente, Operacional, Caixa, Atendente,
 *           Garcom, Balcao, Vendedor, Estoquista, Entregador, Personalizavel
 *
 * v7.0.1 - Adicionado campo 'personalizavel' para perfis onde o admin
 *          pode escolher individualmente quais botoes do dashboard o perfil pode usar.
 */
public class Perfil {
    private int id;
    private String nome;
    private String descricao;
    private boolean sistematico; // perfis do sistema nao podem ser excluidos
    private boolean ativo;
    private boolean personalizavel; // perfil personalizavel pelo admin

    public Perfil() {
        this.ativo = true;
        this.sistematico = false;
        this.personalizavel = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public boolean isSistematico() { return sistematico; }
    public void setSistematico(boolean sistematico) { this.sistematico = sistematico; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public boolean isPersonalizavel() { return personalizavel; }
    public void setPersonalizavel(boolean personalizavel) { this.personalizavel = personalizavel; }

    @Override
    public String toString() { return nome != null ? nome : ""; }
}
