/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 11/3/17.
 * Copyright (c) 2017 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.breadwallet.protocols.messageexchange;

import androidx.test.rule.ActivityTestRule;

import androidx.test.runner.AndroidJUnit4;
import com.breadwallet.crypto.Key;
import com.breadwallet.legacy.presenter.activities.settings.TestActivity;
import com.breadwallet.protocols.messageexchange.entities.EncryptedMessage;
import com.breadwallet.tools.crypto.CryptoHelper;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@Ignore("Signing not yet implemented")
public class EncryptionMessagesTests {

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule = new ActivityTestRule<>(TestActivity.class);

    private String testPrivKey = "a1a8cae79e17cb4ddb4fb6871fcc87f3ee5cbb1049a168657d2c3493d79bfa16";
    private String testPrivKey2 = "a1a8cae79e17cb4ddb4fb6871fcc87f3ee5cbb1049a168657d2c3493d79bfa17";
    //    private String testPubKey = "02d404943960a71535a79679f1cf1df80e70597c05b05722839b38ebc8803af517";
    private String sampleInputData = "b5647811e4472f3ebbadaa9812807785c7ebc04e36d3b6508af7494068fba174";

    @Test
    public void encryptDecrypt() {
        Key key = Key.createFromPrivateKeyString(testPrivKey.getBytes()).get();
        MessageExchangeService messageExchangeService = new MessageExchangeService();
        EncryptedMessage encryptedMessage = messageExchangeService.encrypt(key, key.encodeAsPublic(), CryptoHelper.hexDecode(sampleInputData));
        byte[] decryptedMessage = messageExchangeService.decrypt(key, key.encodeAsPublic(), encryptedMessage.getEncryptedData(), encryptedMessage.getNonce());
        Assert.assertEquals(sampleInputData, CryptoHelper.hexEncode(decryptedMessage));
    }

    @Test
    public void envelopeConstructDeconstruct() {
        Key senderKey = Key.createFromPrivateKeyString(testPrivKey.getBytes()).get();
        Key receiverKey = Key.createFromPrivateKeyString(testPrivKey2.getBytes()).get();
        Protos.Ping ping = Protos.Ping.newBuilder().setPing("Hello ping").build();
        String uniqueId = "myId";
        MessageExchangeService messageExchangeService = new MessageExchangeService();
        EncryptedMessage encryptedMessage = messageExchangeService.encrypt(senderKey, receiverKey.encodeAsPublic(), ping.toByteArray());
        Protos.Envelope envelope = MessageExchangeService.createEnvelope(ByteString.copyFrom(encryptedMessage.getEncryptedData()),
                MessageExchangeService.MessageType.PING, ByteString.copyFrom(senderKey.encodeAsPublic()),
                ByteString.copyFrom(receiverKey.encodeAsPublic()), uniqueId, ByteString.copyFrom(encryptedMessage.getNonce()));
        byte[] decryptedMessage = messageExchangeService.decrypt(senderKey, receiverKey.encodeAsPublic(), envelope.getEncryptedMessage().toByteArray(), encryptedMessage.getNonce());

        try {
            Protos.Ping recoveredPing = Protos.Ping.parseFrom(decryptedMessage);
            Assert.assertEquals(recoveredPing.getPing(), "Hello ping");
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            Assert.fail();
        }

    }

    @Test
    public void verifyEnvelope() {
        Key authKey = Key.createFromPrivateKeyString(testPrivKey.getBytes()).get();
        Key receiverKey = Key.createFromPrivateKeyString(testPrivKey2.getBytes()).get();
        Protos.Ping ping = Protos.Ping.newBuilder().setPing("Hello ping").build();
        String uniqueId = "myId";
        MessageExchangeService messageExchangeService = new MessageExchangeService();
        EncryptedMessage encryptedMessage = messageExchangeService.encrypt(authKey, receiverKey.encodeAsPublic(), ping.toByteArray());

        Protos.Envelope envelope = MessageExchangeService.createEnvelope(ByteString.copyFrom(encryptedMessage.getEncryptedData()),
                MessageExchangeService.MessageType.PING, ByteString.copyFrom(authKey.encodeAsPublic()),
                ByteString.copyFrom(receiverKey.encodeAsPublic()), uniqueId, ByteString.copyFrom(encryptedMessage.getNonce()));
        // TODO: byte[] signature = authKey.compactSign(CryptoHelper.doubleSha256(envelope.toByteArray()));

        // TODO: envelope = envelope.toBuilder().setSignature(ByteString.copyFrom(signature)).build();

        Assert.assertTrue(messageExchangeService.verifyEnvelopeSignature(envelope));
    }

}
