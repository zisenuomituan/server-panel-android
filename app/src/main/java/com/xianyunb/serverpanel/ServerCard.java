package com.xianyunb.serverpanel;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class ServerCard {

    public final View root;
    private final View dot;
    private final TextView name, spec, cpuVal, memVal, netRx, netTx, up;
    private final BarView cpuBar, memBar;

    public ServerCard(View root) {
        this.root = root;
        dot = root.findViewById(R.id.dot);
        name = root.findViewById(R.id.name);
        spec = root.findViewById(R.id.spec);
        cpuVal = root.findViewById(R.id.cpuVal);
        memVal = root.findViewById(R.id.memVal);
        netRx = root.findViewById(R.id.netRx);
        netTx = root.findViewById(R.id.netTx);
        up = root.findViewById(R.id.up);
        cpuBar = root.findViewById(R.id.cpuBar);
        memBar = root.findViewById(R.id.memBar);
    }

    public void bind(Server s, Live l) {
        name.setText(s.name);
        spec.setText(s.vcpu + "c / " + s.memMb + "m");
        dot.getBackground().mutate().setTint(
                ContextCompat.getColor(root.getContext(), Fmt.dotColorRes(l.state)));

        double cpu = l.cpuPct();
        cpuVal.setText(Fmt.pct(cpu) + "%");
        cpuBar.setValue(cpu, Fmt.barColorRes(cpu));

        double mem = l.memPct();
        memVal.setText(Fmt.memText(l));
        memBar.setValue(mem, Fmt.barColorRes(mem));

        netRx.setText("↓ " + Fmt.rate(l.netRxRate));
        netTx.setText("↑ " + Fmt.rate(l.netTxRate));
        up.setText(Fmt.uptime(l.uptime));
    }
}
