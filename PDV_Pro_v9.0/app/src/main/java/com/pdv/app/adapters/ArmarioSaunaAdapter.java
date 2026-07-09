package com.pdv.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter para exibir armarios de sauna em grid com cores por status.
 * 
 * Status:
 * - "livre" -> verde
 * - "ocupado" -> vermelho (chave entregue a um cliente)
 * - "manutencao" -> roxo (indisponivel)
 *
 * v6.9.5 - Modulo de Armarios para Sauna
 * v6.9.8 - OTIMIZACAO: Usa DiffUtil para atualizacoes parciais do RecyclerView,
 *          evitando notifyDataSetChanged() que recria toda a lista visual.
 *          Isso elimina flickering e melhora drasticamente a performance de atualizacao.
 * v7.0.0 - OTIMIZACAO AVANCADA:
 *          - setHasStableIds(true) com getItemId() baseado no ID do armario
 *          - DiffUtil com deteccao de mudancas parciais (payload) para evitar rebind completo
 *          - onBindViewHolder com payload para atualizacoes parciais (so atualiza campos alterados)
 *          - Cores pre-definidas como constantes para evitar alocacoes
 *          - ViewHolder otimizado com cache de visibilidade
 *          - Eliminado flickering em atualizacoes periodicas
 * v7.0.2 - Adicionado suporte a hora de saida (fechamento da conta) no grid
 */
public class ArmarioSaunaAdapter extends RecyclerView.Adapter<ArmarioSaunaAdapter.ArmarioViewHolder> {

    // Constantes de cores para evitar alocacoes repetidas
    private static final int COR_LIVRE_BG = 0xFF1B5E20;
    private static final int COR_LIVRE_TEXT = 0xFF4CAF50;
    private static final int COR_OCUPADO_BG = 0xFFBF360C;
    private static final int COR_OCUPADO_TEXT = 0xFFFF5722;
    private static final int COR_MANUTENCAO_BG = 0xFF4A148C;
    private static final int COR_MANUTENCAO_TEXT = 0xFFCE93D8;

    // Payload keys para atualizacoes parciais
    private static final String PAYLOAD_STATUS = "status";
    private static final String PAYLOAD_CLIENTE = "cliente";
    private static final String PAYLOAD_TEMPO = "tempo";
    private static final String PAYLOAD_TOTAL = "total";
    private static final String PAYLOAD_HORA = "hora";
    private static final String PAYLOAD_HORA_SAIDA = "hora_saida"; // v7.0.2

    public interface OnArmarioClickListener {
        void onArmarioClick(Map<String, Object> armario, int position);
    }

    private List<Map<String, Object>> armarios = new ArrayList<>();
    private OnArmarioClickListener listener;

    public ArmarioSaunaAdapter(OnArmarioClickListener listener) {
        this.listener = listener;
        setHasStableIds(true); // v7.0.0 - Habilitar IDs estaveis para melhor reciclagem
    }

    /**
     * v7.0.0 - Retorna ID estavel baseado no ID do armario no banco.
     * Isso permite ao RecyclerView rastrear itens individuais e evitar rebinds desnecessarios.
     */
    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < armarios.size()) {
            Map<String, Object> armario = armarios.get(position);
            if (armario.get("id") != null) {
                return ((Number) armario.get("id")).longValue();
            }
        }
        return RecyclerView.NO_ID;
    }

    /**
     * v7.0.0 - Usa DiffUtil com suporte a payload para atualizacoes parciais.
     * Campos que mudam frequentemente (tempo_uso, total) sao atualizados via payload
     * sem recriar o ViewHolder inteiro, eliminando flickering.
     */
    public void setArmarios(List<Map<String, Object>> newArmarios) {
        final List<Map<String, Object>> newList = newArmarios != null ? new ArrayList<>(newArmarios) : new ArrayList<>();
        
        if (armarios.isEmpty()) {
            // Primeira carga - usa notifyDataSetChanged por ser mais rapido
            armarios = newList;
            notifyDataSetChanged();
            return;
        }

        final List<Map<String, Object>> oldList = armarios;

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Map<String, Object> oldItem = oldList.get(oldItemPosition);
                Map<String, Object> newItem = newList.get(newItemPosition);
                int oldId = oldItem.get("id") != null ? ((Number) oldItem.get("id")).intValue() : -1;
                int newId = newItem.get("id") != null ? ((Number) newItem.get("id")).intValue() : -2;
                return oldId == newId;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Map<String, Object> oldItem = oldList.get(oldItemPosition);
                Map<String, Object> newItem = newList.get(newItemPosition);

                // Comparar todos os campos relevantes para exibicao
                if (!safeEquals(oldItem.get("status"), newItem.get("status"))) return false;
                if (!safeEquals(oldItem.get("cliente_nome"), newItem.get("cliente_nome"))) return false;
                if (!safeEquals(oldItem.get("tempo_uso"), newItem.get("tempo_uso"))) return false;
                if (!safeEquals(oldItem.get("hora_entrada"), newItem.get("hora_entrada"))) return false;
                // v7.0.2 - Comparar hora de saida
                if (!safeEquals(oldItem.get("hora_saida"), newItem.get("hora_saida"))) return false;

                double oldTotal = oldItem.get("total") != null ? ((Number) oldItem.get("total")).doubleValue() : 0;
                double newTotal = newItem.get("total") != null ? ((Number) newItem.get("total")).doubleValue() : 0;
                if (Math.abs(oldTotal - newTotal) > 0.01) return false;

                return true;
            }

            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                // v7.0.0 - Retorna payload com campos alterados para atualizacao parcial
                Map<String, Object> oldItem = oldList.get(oldItemPosition);
                Map<String, Object> newItem = newList.get(newItemPosition);

                java.util.Set<String> changes = new java.util.HashSet<>();

                if (!safeEquals(oldItem.get("status"), newItem.get("status"))) {
                    changes.add(PAYLOAD_STATUS);
                }
                if (!safeEquals(oldItem.get("cliente_nome"), newItem.get("cliente_nome"))) {
                    changes.add(PAYLOAD_CLIENTE);
                }
                if (!safeEquals(oldItem.get("tempo_uso"), newItem.get("tempo_uso"))) {
                    changes.add(PAYLOAD_TEMPO);
                }
                if (!safeEquals(oldItem.get("hora_entrada"), newItem.get("hora_entrada"))) {
                    changes.add(PAYLOAD_HORA);
                }
                // v7.0.2 - Detectar mudanca na hora de saida
                if (!safeEquals(oldItem.get("hora_saida"), newItem.get("hora_saida"))) {
                    changes.add(PAYLOAD_HORA_SAIDA);
                }

                double oldTotal = oldItem.get("total") != null ? ((Number) oldItem.get("total")).doubleValue() : 0;
                double newTotal = newItem.get("total") != null ? ((Number) newItem.get("total")).doubleValue() : 0;
                if (Math.abs(oldTotal - newTotal) > 0.01) {
                    changes.add(PAYLOAD_TOTAL);
                }

                return changes.isEmpty() ? null : changes;
            }
        });

        armarios = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    private boolean safeEquals(Object a, Object b) {
        String sa = a != null ? a.toString() : "";
        String sb = b != null ? b.toString() : "";
        return sa.equals(sb);
    }

    @NonNull
    @Override
    public ArmarioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_armario_sauna, parent, false);
        return new ArmarioViewHolder(view);
    }

    /**
     * v7.0.0 - onBindViewHolder com suporte a payloads para atualizacao parcial.
     * Quando um payload e fornecido, apenas os campos alterados sao atualizados,
     * evitando rebind completo e eliminando flickering visual.
     */
    @Override
    public void onBindViewHolder(@NonNull ArmarioViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            // Sem payload - bind completo
            onBindViewHolder(holder, position);
            return;
        }

        // v7.0.0 - Atualizacao parcial baseada em payload
        Map<String, Object> armario = armarios.get(position);

        for (Object payload : payloads) {
            if (payload instanceof java.util.Set) {
                @SuppressWarnings("unchecked")
                java.util.Set<String> changes = (java.util.Set<String>) payload;

                if (changes.contains(PAYLOAD_STATUS)) {
                    String status = armario.get("status") != null ? armario.get("status").toString() : "livre";
                    bindStatus(holder, status);
                }
                if (changes.contains(PAYLOAD_CLIENTE)) {
                    String clienteNome = armario.get("cliente_nome") != null ? armario.get("cliente_nome").toString() : "";
                    bindCliente(holder, clienteNome);
                }
                if (changes.contains(PAYLOAD_HORA)) {
                    String horaEntrada = armario.get("hora_entrada") != null ? armario.get("hora_entrada").toString() : "";
                    bindHoraEntrada(holder, horaEntrada);
                }
                // v7.0.2 - Atualizacao parcial da hora de saida
                if (changes.contains(PAYLOAD_HORA_SAIDA)) {
                    String horaSaida = armario.get("hora_saida") != null ? armario.get("hora_saida").toString() : "";
                    bindHoraSaida(holder, horaSaida);
                }
                if (changes.contains(PAYLOAD_TEMPO)) {
                    String tempoUso = armario.get("tempo_uso") != null ? armario.get("tempo_uso").toString() : "";
                    bindTempoUso(holder, tempoUso);
                }
                if (changes.contains(PAYLOAD_TOTAL)) {
                    double total = armario.get("total") != null ? ((Number) armario.get("total")).doubleValue() : 0;
                    bindTotal(holder, total);
                }
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ArmarioViewHolder holder, int position) {
        Map<String, Object> armario = armarios.get(position);

        int numero = armario.get("numero") != null ? ((Number) armario.get("numero")).intValue() : 0;
        String status = armario.get("status") != null ? armario.get("status").toString() : "livre";
        String clienteNome = armario.get("cliente_nome") != null ? armario.get("cliente_nome").toString() : "";
        String localizacao = armario.get("localizacao") != null ? armario.get("localizacao").toString() : "";
        String horaEntrada = armario.get("hora_entrada") != null ? armario.get("hora_entrada").toString() : "";
        // v7.0.2 - Hora de saida
        String horaSaida = armario.get("hora_saida") != null ? armario.get("hora_saida").toString() : "";
        String tempoUso = armario.get("tempo_uso") != null ? armario.get("tempo_uso").toString() : "";
        double total = armario.get("total") != null ? ((Number) armario.get("total")).doubleValue() : 0;

        holder.tvArmarioNumero.setText("Armario " + numero);

        // Bind de cada campo
        bindStatus(holder, status);
        bindLocalizacao(holder, localizacao);
        bindCliente(holder, clienteNome);
        bindHoraEntrada(holder, horaEntrada);
        bindHoraSaida(holder, horaSaida); // v7.0.2
        bindTempoUso(holder, tempoUso);
        bindTotal(holder, total);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    listener.onArmarioClick(armarios.get(adapterPos), adapterPos);
                }
            }
        });
    }

    // ===== Metodos de bind individuais para atualizacao parcial =====

    private void bindStatus(@NonNull ArmarioViewHolder holder, String status) {
        switch (status) {
            case "ocupado":
                holder.tvArmarioStatus.setText("Ocupado");
                holder.tvArmarioStatus.setTextColor(COR_OCUPADO_TEXT);
                holder.cardArmario.setCardBackgroundColor(COR_OCUPADO_BG);
                break;
            case "manutencao":
                holder.tvArmarioStatus.setText("Manutencao");
                holder.tvArmarioStatus.setTextColor(COR_MANUTENCAO_TEXT);
                holder.cardArmario.setCardBackgroundColor(COR_MANUTENCAO_BG);
                break;
            default: // livre
                holder.tvArmarioStatus.setText("Livre");
                holder.tvArmarioStatus.setTextColor(COR_LIVRE_TEXT);
                holder.cardArmario.setCardBackgroundColor(COR_LIVRE_BG);
                break;
        }
    }

    private void bindLocalizacao(@NonNull ArmarioViewHolder holder, String localizacao) {
        if (localizacao != null && !localizacao.isEmpty()) {
            holder.tvArmarioLocalizacao.setText(localizacao);
            holder.tvArmarioLocalizacao.setVisibility(View.VISIBLE);
        } else {
            holder.tvArmarioLocalizacao.setVisibility(View.GONE);
        }
    }

    private void bindCliente(@NonNull ArmarioViewHolder holder, String clienteNome) {
        if (clienteNome != null && !clienteNome.isEmpty()) {
            holder.tvArmarioCliente.setText(clienteNome);
            holder.tvArmarioCliente.setVisibility(View.VISIBLE);
        } else {
            holder.tvArmarioCliente.setVisibility(View.GONE);
        }
    }

    private void bindHoraEntrada(@NonNull ArmarioViewHolder holder, String horaEntrada) {
        if (horaEntrada != null && !horaEntrada.isEmpty()) {
            holder.tvArmarioHoraEntrada.setText("Entrada: " + horaEntrada);
            holder.tvArmarioHoraEntrada.setVisibility(View.VISIBLE);
        } else {
            holder.tvArmarioHoraEntrada.setVisibility(View.GONE);
        }
    }

    /**
     * v7.0.2 - Bind da hora de saida (fechamento da conta) no grid.
     */
    private void bindHoraSaida(@NonNull ArmarioViewHolder holder, String horaSaida) {
        if (horaSaida != null && !horaSaida.isEmpty()) {
            holder.tvArmarioHoraSaida.setText("Saida: " + horaSaida);
            holder.tvArmarioHoraSaida.setVisibility(View.VISIBLE);
        } else {
            holder.tvArmarioHoraSaida.setVisibility(View.GONE);
        }
    }

    private void bindTempoUso(@NonNull ArmarioViewHolder holder, String tempoUso) {
        if (tempoUso != null && !tempoUso.isEmpty()) {
            holder.tvArmarioTempoUso.setText(tempoUso);
            holder.tvArmarioTempoUso.setVisibility(View.VISIBLE);
        } else {
            holder.tvArmarioTempoUso.setVisibility(View.GONE);
        }
    }

    private void bindTotal(@NonNull ArmarioViewHolder holder, double total) {
        if (total > 0) {
            holder.tvArmarioTotal.setText(String.format("R$ %.2f", total));
            holder.tvArmarioTotal.setVisibility(View.VISIBLE);
        } else {
            holder.tvArmarioTotal.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return armarios.size();
    }

    static class ArmarioViewHolder extends RecyclerView.ViewHolder {
        CardView cardArmario;
        TextView tvArmarioNumero, tvArmarioStatus, tvArmarioLocalizacao;
        TextView tvArmarioCliente, tvArmarioHoraEntrada, tvArmarioHoraSaida, tvArmarioTempoUso, tvArmarioTotal;

        ArmarioViewHolder(@NonNull View itemView) {
            super(itemView);
            cardArmario = itemView.findViewById(R.id.cardArmario);
            tvArmarioNumero = itemView.findViewById(R.id.tvArmarioNumero);
            tvArmarioStatus = itemView.findViewById(R.id.tvArmarioStatus);
            tvArmarioLocalizacao = itemView.findViewById(R.id.tvArmarioLocalizacao);
            tvArmarioCliente = itemView.findViewById(R.id.tvArmarioCliente);
            tvArmarioHoraEntrada = itemView.findViewById(R.id.tvArmarioHoraEntrada);
            tvArmarioHoraSaida = itemView.findViewById(R.id.tvArmarioHoraSaida); // v7.0.2
            tvArmarioTempoUso = itemView.findViewById(R.id.tvArmarioTempoUso);
            tvArmarioTotal = itemView.findViewById(R.id.tvArmarioTotal);
        }
    }
}
