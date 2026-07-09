package com.pdv.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.FormatUtils;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class AgendaActivity extends BaseActivity {
    private GenericAdapter<Map<String,Object>> adapter;
    private EditText search;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.AGENDA_ACESSAR)) return;
        ((TextView)findViewById(R.id.tvTitle)).setText("Agenda de Servicos");
        search = findViewById(R.id.etBusca);
        RecyclerView recycler = findViewById(R.id.recyclerView);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder,item,pos) -> {
            holder.setText(R.id.tvLine1, String.valueOf(item.get("line1")));
            holder.setText(R.id.tvLine2, String.valueOf(item.get("line2")));
            ImageView image = holder.find(R.id.ivFoto); if (image != null) image.setVisibility(View.GONE);
            Button edit = holder.find(R.id.btnEditar);
            if (edit != null) {
                edit.setVisibility(PermissionHelper.temPermissao(this, PermissionConstants.AGENDA_EDITAR) ? View.VISIBLE : View.GONE);
                edit.setOnClickListener(v -> openEditor(item));
            }
            Button cancel = holder.find(R.id.btnInativar);
            if (cancel != null) {
                cancel.setText("Cancelar");
                cancel.setVisibility(PermissionHelper.temPermissao(this, PermissionConstants.AGENDA_CANCELAR) ? View.VISIBLE : View.GONE);
                cancel.setOnClickListener(v -> showConfirm("Cancelar agendamento", "Deseja cancelar este agendamento?",
                        () -> cancel(((Number)item.get("id")).intValue())));
            }
        });
        recycler.setAdapter(adapter);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.AGENDA_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.AGENDA_CRIAR)) openEditor(null);
        });
        findViewById(R.id.btnBuscar).setOnClickListener(v -> load());
        search.setOnEditorActionListener((v,a,e) -> { load(); return true; });
        load();
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        String term = search != null ? search.getText().toString().trim() : "";
        showLoading("Carregando agenda...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT a.*,COALESCE(c.nome,'Sem cliente') cliente,COALESCE(s.descricao,'Servico livre') servico "
                                + "FROM agenda_servicos a LEFT JOIN clientes c ON a.cliente_id=c.id "
                                + "LEFT JOIN servicos s ON a.servico_id=s.id WHERE a.status<>'cancelado' "
                                + "AND (a.titulo LIKE ? OR c.nome LIKE ? OR s.descricao LIKE ?) "
                                + "ORDER BY a.data_hora LIMIT 500");
                String like = "%" + term + "%";
                ps.setString(1,like); ps.setString(2,like); ps.setString(3,like);
                ResultSet rs = ps.executeQuery();
                List<Map<String,Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("id",rs.getInt("id")); row.put("titulo",rs.getString("titulo"));
                    row.put("cliente_id",rs.getInt("cliente_id")); row.put("servico_id",rs.getInt("servico_id"));
                    row.put("data_hora",rs.getString("data_hora")); row.put("duracao",rs.getInt("duracao_minutos"));
                    row.put("alerta",rs.getInt("alerta_minutos")); row.put("observacao",rs.getString("observacao"));
                    row.put("line1",rs.getString("titulo") + " - " + rs.getString("cliente"));
                    row.put("line2",FormatUtils.formatDate(rs.getString("data_hora")) + " | " + rs.getString("servico")
                            + " | Alerta " + rs.getInt("alerta_minutos") + " min antes");
                    list.add(row);
                }
                rs.close(); ps.close(); hideLoading(); runOnUiThread(() -> adapter.setItems(list));
            } catch (Exception e) { hideLoading(); showErrorFromException(e, ErrorHandler.CTX_CARREGAR); }
        }).start();
    }

    private void openEditor(Map<String,Object> record) {
        String permissao = record == null ? PermissionConstants.AGENDA_CRIAR : PermissionConstants.AGENDA_EDITAR;
        if (!PermissionHelper.verificar(this, permissao)) return;
        showLoading("Preparando agenda...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                List<Integer> clientIds = new ArrayList<>(); List<String> clients = new ArrayList<>();
                clientIds.add(0); clients.add("Sem cliente");
                Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT id,nome FROM clientes WHERE ativo=1 ORDER BY nome");
                while(rs.next()){ clientIds.add(rs.getInt(1)); clients.add(rs.getString(2)); } rs.close(); stmt.close();
                List<Integer> serviceIds = new ArrayList<>(); List<String> services = new ArrayList<>();
                serviceIds.add(0); services.add("Servico livre");
                stmt=conn.createStatement(); rs=stmt.executeQuery("SELECT id,descricao FROM servicos WHERE ativo=1 ORDER BY descricao");
                while(rs.next()){ serviceIds.add(rs.getInt(1)); services.add(rs.getString(2)); } rs.close(); stmt.close();
                hideLoading(); runOnUiThread(() -> showEditor(record,clientIds,clients,serviceIds,services));
            } catch(Exception e){ hideLoading(); showErrorFromException(e,ErrorHandler.CTX_CARREGAR); }
        }).start();
    }

    private void showEditor(Map<String,Object> record,List<Integer> clientIds,List<String> clients,
                            List<Integer> serviceIds,List<String> services) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density); form.setPadding(pad,pad/2,pad,0);
        EditText title=field("Titulo do agendamento",record,"titulo"); form.addView(title);
        Spinner client=new Spinner(this); client.setAdapter(spinner(clients)); form.addView(client);
        Spinner service=new Spinner(this); service.setAdapter(spinner(services)); form.addView(service);
        EditText when=field("Data e hora (dd/MM/aaaa HH:mm)",null,null);
        String defaultWhen=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new java.util.Date(System.currentTimeMillis()+3600000));
        if(record!=null){ try{ java.util.Date d=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).parse(String.valueOf(record.get("data_hora"))); when.setText(new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(d)); }catch(Exception e){when.setText(defaultWhen);} } else when.setText(defaultWhen);
        form.addView(when);
        EditText duration=field("Duracao em minutos",record,"duracao"); if(duration.getText().length()==0) duration.setText("60"); duration.setInputType(2); form.addView(duration);
        EditText alert=field("Alertar quantos minutos antes",record,"alerta"); if(alert.getText().length()==0) alert.setText("30"); alert.setInputType(2); form.addView(alert);
        EditText obs=field("Observacao",record,"observacao"); form.addView(obs);
        if(record!=null){ selectId(client,clientIds,number(record.get("cliente_id"))); selectId(service,serviceIds,number(record.get("servico_id"))); }
        new AlertDialog.Builder(this).setTitle(record==null?"Novo Agendamento":"Editar Agendamento").setView(form)
                .setPositiveButton("Salvar",(d,w)->save(record==null?0:number(record.get("id")),title.getText().toString().trim(),
                        clientIds.get(client.getSelectedItemPosition()),serviceIds.get(service.getSelectedItemPosition()),
                        when.getText().toString().trim(),duration.getText().toString(),alert.getText().toString(),obs.getText().toString().trim()))
                .setNegativeButton("Cancelar",null).show();
    }

    private ArrayAdapter<String> spinner(List<String> values){ ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);return a; }
    private EditText field(String hint,Map<String,Object> record,String key){ EditText e=new EditText(this);e.setHint(hint);e.setTextColor(0xFFFFFFFF);e.setHintTextColor(0xFF90A4AE);e.setBackgroundResource(R.drawable.input_bg);e.setPadding(24,20,24,20); if(record!=null&&key!=null&&record.get(key)!=null)e.setText(String.valueOf(record.get(key)));return e; }
    private int number(Object o){return o instanceof Number?((Number)o).intValue():0;}
    private void selectId(Spinner s,List<Integer> ids,int id){int i=ids.indexOf(id);if(i>=0)s.setSelection(i);}

    private void save(int id,String title,int client,int service,String when,String duration,String alert,String obs){
        if(title.isEmpty()){showError("Informe o titulo do agendamento.");return;}
        java.util.Date parsed; try{parsed=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).parse(when);}catch(Exception e){showError("Informe data e hora no formato dd/MM/aaaa HH:mm.");return;}
        int dur=60,alt=30;try{dur=Integer.parseInt(duration);}catch(Exception ignored){}try{alt=Integer.parseInt(alert);}catch(Exception ignored){}
        final int fdur=Math.max(1,dur),falt=Math.max(0,alt); final java.util.Date fdate=parsed;
        showLoading("Salvando agendamento...");new Thread(()->{try{Connection conn=DatabaseHelper.getInstance(this).getConnection();PreparedStatement ps;
            if(id==0)ps=conn.prepareStatement("INSERT INTO agenda_servicos (titulo,cliente_id,servico_id,data_hora,duracao_minutos,alerta_minutos,observacao,status,alertado,usuario_id) VALUES (?,?,?,?,?,?,?,'agendado',0,?)");
            else ps=conn.prepareStatement("UPDATE agenda_servicos SET titulo=?,cliente_id=?,servico_id=?,data_hora=?,duracao_minutos=?,alerta_minutos=?,observacao=?,status='agendado',alertado=0 WHERE id=?");
            ps.setString(1,title);if(client>0)ps.setInt(2,client);else ps.setNull(2,Types.INTEGER);if(service>0)ps.setInt(3,service);else ps.setNull(3,Types.INTEGER);ps.setTimestamp(4,new Timestamp(fdate.getTime()));ps.setInt(5,fdur);ps.setInt(6,falt);ps.setString(7,obs);
            if(id==0){int uid=getSharedPreferences("session",MODE_PRIVATE).getInt("user_id",0);if(uid>0)ps.setInt(8,uid);else ps.setNull(8,Types.INTEGER);}else ps.setInt(8,id);
            ps.executeUpdate();ps.close();hideLoading();showToast("Agendamento salvo.");load();}catch(Exception e){hideLoading();showErrorFromException(e,ErrorHandler.CTX_SALVAR);}}).start();
    }
    private void cancel(int id){showLoading("Cancelando...");new Thread(()->{try{PreparedStatement ps=DatabaseHelper.getInstance(this).getConnection().prepareStatement("UPDATE agenda_servicos SET status='cancelado' WHERE id=?");ps.setInt(1,id);ps.executeUpdate();ps.close();hideLoading();load();}catch(Exception e){hideLoading();showErrorFromException(e,ErrorHandler.CTX_EXCLUIR);}}).start();}
}
