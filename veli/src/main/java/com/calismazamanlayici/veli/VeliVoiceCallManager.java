package com.calismazamanlayici.veli;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VeliVoiceCallManager {

    public static final int AUDIO_UDP_PORT = 45680;
    private static final int SAMPLE_RATE = 8000;
    private static final int PACKET_BYTES = 640;

    private static volatile boolean callRunning = false;
    private static DatagramSocket udpSocket;
    private static AudioRecord audioRecord;
    private static AudioTrack audioTrack;
    private static String remotePeerIp;

    public static synchronized void startCall(final Context context, final String targetIp) {
        stopCall(context);
        remotePeerIp = targetIp;
        callRunning = true;

        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                am.setSpeakerphoneOn(true);
            }
        } catch (Exception ignored) {
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                runAudioLoop();
            }
        }, "VeliVoiceCallThread").start();
    }

    private static void runAudioLoop() {
        try {
            udpSocket = new DatagramSocket(AUDIO_UDP_PORT);
            udpSocket.setSoTimeout(2000);

            int minRecBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int recBufSize = Math.max(minRecBuf * 2, PACKET_BYTES * 4);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recBufSize
            );

            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    AcousticEchoCanceler aec = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
                    if (aec != null) aec.setEnabled(true);
                } catch (Exception ignored) {
                }
            }

            int minPlayBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int playBufSize = Math.max(minPlayBuf * 2, PACKET_BYTES * 4);

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    playBufSize,
                    AudioTrack.MODE_STREAM
            );

            audioRecord.startRecording();
            audioTrack.play();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] recvBuf = new byte[PACKET_BYTES];
                    while (callRunning && udpSocket != null && !udpSocket.isClosed()) {
                        try {
                            DatagramPacket pkt = new DatagramPacket(recvBuf, recvBuf.length);
                            udpSocket.receive(pkt);
                            if (pkt.getLength() > 0 && audioTrack != null) {
                                audioTrack.write(pkt.getData(), 0, pkt.getLength());
                                String senderIp = pkt.getAddress().getHostAddress();
                                if (senderIp != null && !senderIp.isEmpty()) {
                                    remotePeerIp = senderIp;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }, "VeliVoiceRecvThread").start();

            byte[] sendBuf = new byte[PACKET_BYTES];
            while (callRunning && audioRecord != null) {
                int read = audioRecord.read(sendBuf, 0, sendBuf.length);
                if (read > 0 && remotePeerIp != null && !remotePeerIp.isEmpty() && udpSocket != null) {
                    try {
                        InetAddress addr = InetAddress.getByName(remotePeerIp);
                        DatagramPacket outPkt = new DatagramPacket(sendBuf, read, addr, AUDIO_UDP_PORT);
                        udpSocket.send(outPkt);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            cleanupResources();
        }
    }

    public static synchronized void stopCall(Context context) {
        callRunning = false;
        cleanupResources();
        if (context != null) {
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.setSpeakerphoneOn(false);
                    am.setMode(AudioManager.MODE_NORMAL);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void cleanupResources() {
        try {
            if (udpSocket != null) {
                udpSocket.close();
                udpSocket = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        } catch (Exception ignored) {
        }
    }
}
