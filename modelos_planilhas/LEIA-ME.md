# Modelos de planilha para importação (PDV Pro Desktop)

Use estes arquivos como base para importar **Produtos** e **Clientes** pelo botão
**"⬇ Importar Planilha"** nas telas de Cadastro de Produtos e Cadastro de Clientes.
Você também pode gerar o modelo direto no sistema pelo botão **"⬆ Baixar Modelo"**.

- Formatos aceitos: **CSV** (separador `;` ou `,`) e **XLSX** (Excel; requer o pacote `openpyxl`).
- Mantenha a **linha de cabeçalho** (nomes das colunas). As linhas de exemplo podem ser apagadas.
- Valores numéricos aceitam vírgula ou ponto decimal (ex.: `5,00` ou `5.00`).
- Coluna `ativo`: use `1`/`0` (ou `sim`/`nao`).

## Produtos — `modelo_produtos.csv`

| Coluna | Obrigatória | Descrição |
|---|---|---|
| `codigo` | Não | Código interno. Se vazio, o sistema gera automaticamente. |
| `codigo_barras` | Não | EAN/GTIN. Usado para localizar/atualizar produto existente. |
| `descricao` | **Sim** | Nome do produto. |
| `tipo` | Não | Categoria/tipo (nome). Criada automaticamente se não existir. |
| `unidade` | Não | Ex.: `UN`, `KG`, `L`. Padrão: `UN`. |
| `preco_custo` | Não | Preço de custo. Padrão: `0`. |
| `preco_venda` | **Sim** | Preço de venda. |
| `estoque` | Não | Estoque atual. Padrão: `0`. |
| `estoque_minimo` | Não | Estoque mínimo. Padrão: `0`. |
| `ativo` | Não | `1` ativo / `0` inativo. Padrão: `1`. |

Atualização x criação: se `codigo` (ou, na falta dele, `codigo_barras`) já existir, o
produto é **atualizado**; caso contrário, é **criado**.

## Clientes — `modelo_clientes.csv`

| Coluna | Obrigatória | Descrição |
|---|---|---|
| `nome` | **Sim** | Nome/razão social do cliente. |
| `cpf_cnpj` | Não | CPF ou CNPJ. Usado para localizar/atualizar cliente existente. |
| `celular` | Não | Telefone/WhatsApp. |
| `endereco` | Não | Logradouro. |
| `numero` | Não | Número. |
| `bairro` | Não | Bairro. |
| `cidade` | Não | Cidade. |
| `uf` | Não | UF (2 letras). |
| `cep` | Não | CEP. |
| `email` | Não | E-mail. |
| `ativo` | Não | `1` ativo / `0` inativo. Padrão: `1`. |

Atualização x criação: se `cpf_cnpj` já existir, o cliente é **atualizado**; caso
contrário, é **criado**.

> Os cabeçalhos aceitam variações comuns (ex.: `preço`, `valor`, `categoria`,
> `telefone`, `cpf`, `cnpj`), sem depender de acentos ou maiúsculas.
