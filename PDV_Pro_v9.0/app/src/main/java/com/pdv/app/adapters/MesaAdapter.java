package com.pdv.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter para exibir mesas em grid com cores por status.
 * 
 * Status:
 * - "livre" -> verde
 * - "ocupada" -> vermelho (tem produtos)
 * - "reservada" -> laranja
 * - "pronta" -> azul (pronta para cobranca)
 *
 * v6.7.8 - Exibe "Reservado por [nome]" para mesas reservadas
 * v6.8.0 - Adicionado status "pronta" (azul) para mesas prontas para cobranca
 */
public class MesaAdapter extends RecyclerView.Adapter<MesaAdapter.MesaViewHolder> {

    public interface OnMesaClickListener {
        void onMesaClick(Map<String, Object> mesa, int position);
    }

    private List<Map<String, Object>> mesas = new ArrayList<>();
    private OnMesaClickListener listener;

    public MesaAdapter(OnMesaClickListener listener) {
        this.listener = listener;
    }

    public void setMesas(List<Map<String, Object>> mesas) {
        this.mesas = mesas != null ? mesas : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MesaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mesa, parent, false);
        return new MesaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MesaViewHolder holder, int position) {
        Map<String, Object> mesa = mesas.get(position);

        int numero = mesa.get("numero") != null ? ((Number) mesa.get("numero")).intValue() : 0;
        String status = mesa.get("status") != null ? mesa.get("status").toString() : "livre";
        String garcomNome = mesa.get("garcom_nome") != null ? mesa.get("garcom_nome").toString() : "";
        int pessoas = mesa.get("qtd_pessoas") != null ? ((Number) mesa.get("qtd_pessoas")).intValue() : 0;
        double total = mesa.get("total") != null ? ((Number) mesa.get("total")).doubleValue() : 0;
        int qtdItens = mesa.get("qtd_itens") != null ? ((Number) mesa.get("qtd_itens")).intValue() : 0;

        // v6.7.8 - Dados de reserva
        String reservadoPorNome = mesa.get("reservado_por_usuario_nome") != null ? mesa.get("reservado_por_usuario_nome").toString() : "";

        holder.tvMesaNumero.setText("Mesa " + numero);

        // Status e cor
        switch (status) {
            case "ocupada":
                holder.tvMesaStatus.setText("Ocupada");
                holder.tvMesaStatus.setTextColor(0xFFFF5722);
                holder.cardMesa.setCardBackgroundColor(0xFFBF360C);
                break;
            case "reservada":
                holder.tvMesaStatus.setText("Reservada");
                holder.tvMesaStatus.setTextColor(0xFFFF9800);
                holder.cardMesa.setCardBackgroundColor(0xFFE65100);
                break;
            case "pronta":
                holder.tvMesaStatus.setText("Pronta p/ Cobran\u00e7a");
                holder.tvMesaStatus.setTextColor(0xFF42A5F5);
                holder.cardMesa.setCardBackgroundColor(0xFF0D47A1);
                break;
            default: // livre
                holder.tvMesaStatus.setText("Livre");
                holder.tvMesaStatus.setTextColor(0xFF4CAF50);
                holder.cardMesa.setCardBackgroundColor(0xFF1B5E20);
                break;
        }

        // v6.7.8 - Exibir "Reservado por [nome]"
        if ("reservada".equals(status) && !reservadoPorNome.isEmpty()) {
            holder.tvMesaReservadoPor.setText("Reservado por: " + reservadoPorNome);
            holder.tvMesaReservadoPor.setVisibility(View.VISIBLE);
        } else {
            holder.tvMesaReservadoPor.setVisibility(View.GONE);
        }

        // Garcom
        if (!garcomNome.isEmpty()) {
            holder.tvMesaGarcom.setText("Garcom: " + garcomNome);
            holder.tvMesaGarcom.setVisibility(View.VISIBLE);
        } else {
            holder.tvMesaGarcom.setVisibility(View.GONE);
        }

        // Pessoas
        if (pessoas > 0) {
            holder.tvMesaPessoas.setText(pessoas + " pessoa(s)");
            holder.tvMesaPessoas.setVisibility(View.VISIBLE);
        } else {
            holder.tvMesaPessoas.setVisibility(View.GONE);
        }

        // Total
        if (total > 0) {
            holder.tvMesaTotal.setText(String.format("R$ %.2f", total));
            holder.tvMesaTotal.setVisibility(View.VISIBLE);
        } else {
            holder.tvMesaTotal.setVisibility(View.GONE);
        }

        // Itens
        if (qtdItens > 0) {
            holder.tvMesaItens.setText(qtdItens + " item(ns)");
            holder.tvMesaItens.setVisibility(View.VISIBLE);
        } else {
            holder.tvMesaItens.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMesaClick(mesa, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mesas.size();
    }

    static class MesaViewHolder extends RecyclerView.ViewHolder {
        CardView cardMesa;
        TextView tvMesaNumero, tvMesaStatus, tvMesaGarcom, tvMesaPessoas, tvMesaTotal, tvMesaItens;
        TextView tvMesaReservadoPor; // v6.7.8

        MesaViewHolder(@NonNull View itemView) {
            super(itemView);
            cardMesa = itemView.findViewById(R.id.cardMesa);
            tvMesaNumero = itemView.findViewById(R.id.tvMesaNumero);
            tvMesaStatus = itemView.findViewById(R.id.tvMesaStatus);
            tvMesaGarcom = itemView.findViewById(R.id.tvMesaGarcom);
            tvMesaPessoas = itemView.findViewById(R.id.tvMesaPessoas);
            tvMesaTotal = itemView.findViewById(R.id.tvMesaTotal);
            tvMesaItens = itemView.findViewById(R.id.tvMesaItens);
            tvMesaReservadoPor = itemView.findViewById(R.id.tvMesaReservadoPor); // v6.7.8
        }
    }
}
