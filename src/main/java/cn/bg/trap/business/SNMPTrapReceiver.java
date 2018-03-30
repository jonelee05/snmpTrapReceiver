package cn.bg.trap.business;

import org.snmp4j.*;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Vector;

/**
 * Created by lq on 2018/3/19.
 */
@Service
public class SNMPTrapReceiver implements CommandResponder {

    @Value("${port:162}")
    String port;
    @Value("${community:public}")
    String community;
    @Value("${securityName:securityName}")
    String securityName;
    //    @Value("${securityName:securityName}")
//    String securityName;
    @Value("${authPassword:authPassword}")
    String authPassword;
    @Value("${priPassword:priPassword}")
    String priPassword;
    @Value("${priProtocol:aes}")
    String priProtocol;
    @Value("${authProtocol:md5}")
    String authProtocol;

    private OID priProtocolBean;
    private OID authProtocolBean;

    private Snmp snmp = null;
    private int n = 0;
    private long start = -1;

    public SNMPTrapReceiver() {
    }

    public void run() {
        try {
            init();
            snmp.addCommandResponder(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void setPriProtocolBean() {
        if (priProtocol.equalsIgnoreCase("des")) {
            this.priProtocolBean = PrivDES.ID;
        } else if (priProtocol.equalsIgnoreCase("aes128") || priProtocol.equalsIgnoreCase("aes")) {
            this.priProtocolBean = PrivAES128.ID;
        } else if (priProtocol.equalsIgnoreCase("aes192")) {
            this.priProtocolBean = PrivAES192.ID;
        } else if (priProtocol.equalsIgnoreCase("aes256")) {
            this.priProtocolBean = PrivAES256.ID;
        } else {
            this.priProtocolBean = null;
        }
    }

    private void setAuthProtocolBean() {
        if (authProtocol.equalsIgnoreCase("md5")) {
            this.authProtocolBean = AuthMD5.ID;
        } else if (authProtocol.equalsIgnoreCase("sha")) {
            this.authProtocolBean = AuthSHA.ID;
        } else {
            this.authProtocolBean = null;
        }
    }

    private void init() throws UnknownHostException, IOException {
        ThreadPool threadPool = ThreadPool.create("Trap", 1);
        MultiThreadedMessageDispatcher dispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());
        Address listenAddress = GenericAddress.parse("udp:0.0.0.0/" + port);
        TransportMapping<?> transport;
        transport = new DefaultUdpTransportMapping((UdpAddress) listenAddress);
        USM usm = new USM(
                SecurityProtocols.getInstance().addDefaultProtocols(),
                new OctetString(MPv3.createLocalEngineID()), 0);
        usm.setEngineDiscoveryEnabled(true);

        snmp = new Snmp(dispatcher, transport);
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv3(usm));

        SecurityModels.getInstance().addSecurityModel(usm);
        setPriProtocolBean();
        setAuthProtocolBean();
        snmp.getUSM().addUser(
                new OctetString(securityName),
                new UsmUser(new OctetString(securityName), authProtocolBean,
                        new OctetString(authPassword), priProtocolBean,
                        new OctetString(priPassword)));

        snmp.listen();
        System.out.println("listen udp:0.0.0.0/" + port);
    }

    @Override
    public void processPdu(CommandResponderEvent event) {
        if (event.getSecurityModel() == 1 || event.getSecurityModel() == 2) {
            if (!community.equals(new String(event.getSecurityName()))) {
                return;
            }
        }
        System.out.println("Received PDU......");
        if (start < 0) {
            start = System.currentTimeMillis() - 1;
        }
        n++;
        if ((n % 100 == 1)) {
            System.out.println("Processed "
                    + (n / (double) (System.currentTimeMillis() - start))
                    * 1000 + "/s, total=" + n);
        }

        StringBuffer msg = new StringBuffer();
        msg.append(event.toString());
        Vector<? extends VariableBinding> varBinds = event.getPDU()
                .getVariableBindings();
        if (varBinds != null && !varBinds.isEmpty()) {
            Iterator<? extends VariableBinding> varIter = varBinds.iterator();
            while (varIter.hasNext()) {
                VariableBinding var = varIter.next();
                msg.append(var.toString()).append(";");
            }
        }
        System.out.println("Message Received: " + msg.toString());
        System.out.println("Message PDU Type:" + event.getPDU().getType());
        PDU pdu = event.getPDU();
        if (pdu != null) {
            System.out.println("Variables:");
            pdu.getVariableBindings().forEach(varBind -> {
                System.out.println(varBind.toString());
            });
        }
    }
}