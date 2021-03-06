/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tron.core;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tron.crypto.ECKey;
import org.tron.datasource.leveldb.LevelDbDataSource;
import org.tron.protos.core.TronTXOutput;
import org.tron.protos.core.TronTXOutputs;
import org.tron.protos.core.TronTXOutputs.TXOutputs;
import org.tron.utils.ByteArray;

import java.util.*;

import static org.tron.core.Constant.TRANSACTION_DB_NAME;

public class UTXOSet {
    private static final Logger logger = LoggerFactory.getLogger("UTXOSet");

    private Blockchain blockchain;
    private LevelDbDataSource txDB = null;

    public UTXOSet() {
        txDB = new LevelDbDataSource(TRANSACTION_DB_NAME);
        txDB.init();
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    public void reindex() {
        logger.info("reindex");

        txDB.reset();

        HashMap<String, TXOutputs> utxo = blockchain.findUTXO();

        Set<Map.Entry<String, TXOutputs>> entrySet = utxo.entrySet();

        for (Map.Entry<String, TXOutputs> entry : entrySet) {
            String key = entry.getKey();
            TXOutputs value = entry.getValue();

            for (TronTXOutput.TXOutput txOutput : value.getOutputsList()) {
                txDB.put(ByteArray.fromHexString(key), value.toByteArray());
            }
        }
    }

    public SpendableOutputs findSpendableOutputs(byte[] pubKeyHash, long amount) {
        SpendableOutputs spendableOutputs = new SpendableOutputs();
        HashMap<String, long[]> unspentOutputs = new HashMap<>();
        long accumulated = 0L;

        Set<byte[]> keySet = txDB.keys();

        for (byte[] key : keySet) {
            byte[] txOutputsData = txDB.get(key);
            try {
                TXOutputs txOutputs = TronTXOutputs.TXOutputs.parseFrom(txOutputsData);

                int len = txOutputs.getOutputsCount();

                for (int i = 0; i < len; i++) {
                    TronTXOutput.TXOutput txOutput = txOutputs.getOutputs(i);
                    if (ByteArray.toHexString(ECKey.computeAddress(pubKeyHash)).equals(ByteArray.toHexString(txOutput
                            .getPubKeyHash()
                            .toByteArray())) && accumulated < amount) {
                        accumulated += txOutput.getValue();

                        long[] v = unspentOutputs.get(ByteArray.toHexString(key));

                        if (v == null) {
                            v = new long[0];
                        }

                        long[] tmp = Arrays.copyOf(v, v.length + 1);
                        tmp[tmp.length - 1] = i;

                        unspentOutputs.put(ByteArray.toHexString(key), tmp);
                    }
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        spendableOutputs.setAmount(accumulated);
        spendableOutputs.setUnspentOutputs(unspentOutputs);

        return spendableOutputs;
    }

    public ArrayList<TronTXOutput.TXOutput> findUTXO(byte[] pubKeyHash) {
        ArrayList<TronTXOutput.TXOutput> utxos = new ArrayList<>();

        Set<byte[]> keySet = txDB.keys();
        for (byte[] key : keySet) {
            byte[] txData = txDB.get(key);

            try {
                TXOutputs txOutputs = TXOutputs.parseFrom(txData);
                for (TronTXOutput.TXOutput txOutput : txOutputs.getOutputsList()) {
                    if (ByteArray.toHexString(ECKey.computeAddress(pubKeyHash)).equals(ByteArray.toHexString(txOutput
                            .getPubKeyHash()
                            .toByteArray()))) {
                        utxos.add(txOutput);
                    }
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        return utxos;
    }
}
