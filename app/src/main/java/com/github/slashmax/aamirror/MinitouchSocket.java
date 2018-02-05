package com.github.slashmax.aamirror;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

public class MinitouchSocket
{
    private static final String TAG = "MinitouchSocket";
    private static final String DEFAULT_SOCKET_NAME = "minitouch";

    private LocalSocket     m_Socket;
    private OutputStream    m_Output;

    private int      Version;
    private int      MaxContact;
    private double   MaxX;
    private double   MaxY;
    private double   MaxPressure;
    private int      Pid;

    private double   m_ProjectionOffsetX;
    private double   m_ProjectionOffsetY;
    private double   m_ProjectionWidth;
    private double   m_ProjectionHeight;

    boolean connect()
    {
        Log.d(TAG, "connect");

        disconnect();
        LocalSocket socket = new LocalSocket();
        try
        {
            socket.connect(new LocalSocketAddress(DEFAULT_SOCKET_NAME));
            if (inputReadParams(socket.getInputStream()))
            {
                m_Output = socket.getOutputStream();
                m_Socket = socket;
            }
            else
            {
                socket.close();
            }
        }
        catch (Exception e)
        {
            Log.d(TAG, "connect exception: " + e.toString());
        }
        return isConnected();
    }

    void disconnect()
    {
        Log.d(TAG, "disconnect");
        if (isConnected())
        {
            try
            {
                m_Socket.close();
            }
            catch (Exception e)
            {
                Log.d(TAG, "disconnect exception: " + e.toString());
            }
            m_Output = null;
            m_Socket = null;
        }
    }

    boolean isConnected()
    {
        return (m_Socket != null);
    }

    int getPid()
    {
        Log.d(TAG, "getPid: " + Pid);
        return Pid;
    }

    private boolean inputReadParams(InputStream stream)
    {
        Log.d(TAG, "inputReadParams");
        byte[] data_buffer = new byte[128];

        Pid = 0;

        try
        {
            if (stream.read(data_buffer) == -1)
            {
                Log.d(TAG, "inputReadParams read error");
                return false;
            }
        }
        catch (Exception e)
        {
            Log.d(TAG, "inputReadParams read exception: " + e.toString());
            return false;
        }

        String data_string = new String(data_buffer);
        String[] lines = data_string.split("\n");

        if (lines.length < 3)
        {
            Log.d(TAG, "inputReadParams error: less then 3 lines");
            return false;
        }

        String[] version_line = lines[0].split(" ");
        if (version_line.length == 2)
        {
            Version = Integer.parseInt(version_line[1]);
        }
        String[] limits_line = lines[1].split(" ");
        if (limits_line.length == 5)
        {
            MaxContact = Integer.parseInt(limits_line[1]);
            MaxX = Integer.parseInt(limits_line[2]);
            MaxY = Integer.parseInt(limits_line[3]);
            MaxPressure = Integer.parseInt(limits_line[4]);
        }
        String[] pid_line = lines[2].split(" ");
        if (pid_line.length == 2)
        {
            Pid = Integer.parseInt(pid_line[1]);
        }

        Log.d(TAG, "inputReadParams: pid " + Pid);
        return true;
    }

    private boolean OutputWrite(String command)
    {
        if (m_Output == null)
            return false;

        boolean ok = true;
        try
        {
            m_Output.write(command.getBytes());
        }
        catch (Exception e)
        {
            ok = false;
        }
        return ok;
    }

    private boolean ValidateBounds(double x, double y)
    {
        return (x >= 0.0 && x < MaxX && y >= 0.0 && y < MaxY);
    }

    void  UpdateTouchTransformations(double screenWidth, double screenHeight)
    {
        double factX = MaxX / screenWidth;
        double factY = MaxY / screenHeight;

        double fact = (factX < factY ? factX : factY);

        m_ProjectionWidth = fact * screenWidth;
        m_ProjectionHeight = fact * screenHeight;

        m_ProjectionOffsetX = (MaxX - m_ProjectionWidth) / 2.0;
        m_ProjectionOffsetY = (MaxY - m_ProjectionHeight) / 2.0;
    }

    boolean TouchDown(int id, double x, double y, double pressure)
    {
        x = m_ProjectionOffsetX + x * m_ProjectionWidth;
        y = m_ProjectionOffsetY + y * m_ProjectionHeight;

        if (!ValidateBounds(x, y))
            return true;

        pressure = pressure * MaxPressure;
        return OutputWrite(String.format("d %d %d %d %d\n", id, (int)x, (int)y, (int)pressure));
    }

    boolean TouchMove(int id, double x, double y, double pressure)
    {
        x = m_ProjectionOffsetX + x * m_ProjectionWidth;
        y = m_ProjectionOffsetY + y * m_ProjectionHeight;

        if (!ValidateBounds(x, y))
            return true;

        pressure = pressure * MaxPressure;
        return OutputWrite(String.format("m %d %d %d %d\n", id, (int)x, (int)y, (int)pressure));
    }

    boolean TouchUp(int id)
    {
        return OutputWrite(String.format("u %d\n", id));
    }

    boolean TouchUpAll()
    {
        boolean ok = true;
        for (int i = 0; i < MaxContact; i++)
            ok = ok && TouchUp(i);
        return ok;
    }

    boolean TouchCommit()
    {
        return OutputWrite("c\n");
    }

    public boolean TouchReset()
    {
        return OutputWrite("r\n");
    }
}
