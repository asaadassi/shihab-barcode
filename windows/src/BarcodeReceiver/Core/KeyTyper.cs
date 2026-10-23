using System.Runtime.InteropServices;

namespace BarcodeReceiver.Core;

/// <summary>One key to press: a virtual key (with Shift when the layout needs it) or, when the layout has no key for
/// the character, a Unicode key event.</summary>
internal readonly record struct KeyStroke(ushort Vk, bool Shift, char Unicode)
{
    public bool IsUnicode => Vk == 0;
}

internal enum TypeResult { Typed, Busy, Blocked }

/// <summary>
/// Types text into the active window with SendInput, as a keyboard would: each character becomes the key the
/// foreground window's keyboard layout uses for it (VkKeyScanEx), so web pages see real digit keys; Enter for "\n",
/// Tab for "\t"; a Unicode event for characters the layout lacks.
/// </summary>
internal static class KeyTyper
{
    public const ushort VK_RETURN = 0x0D, VK_TAB = 0x09;

    /// <summary>The keys for <paramref name="text"/>. <paramref name="scan"/> is VkKeyScanEx for the layout (low byte the
    /// key, high byte the shift state: 1 Shift, 2 Ctrl, 4 Alt; -1 none). Pure, for tests.</summary>
    public static List<KeyStroke> Plan(string text, Func<char, short> scan)
    {
        var keys = new List<KeyStroke>(text.Length);
        for (int i = 0; i < text.Length; i++)
        {
            var c = text[i];
            if (c == '\r')
            {
                if (i + 1 < text.Length && text[i + 1] == '\n') continue;   // "\r\n" is one Enter
                keys.Add(new KeyStroke(VK_RETURN, false, '\0'));
            }
            else if (c == '\n') keys.Add(new KeyStroke(VK_RETURN, false, '\0'));
            else if (c == '\t') keys.Add(new KeyStroke(VK_TAB, false, '\0'));
            else if (char.IsControl(c)) continue;
            else
            {
                var s = scan(c);
                var vk = (byte)(s & 0xFF);
                var mods = (s >> 8) & 0xFF;
                // Ctrl or Alt combinations (AltGr signs) would trigger shortcuts in some apps: type those as Unicode.
                if (s == -1 || vk == 0xFF || (mods & 0x06) != 0) keys.Add(new KeyStroke(0, false, c));
                else keys.Add(new KeyStroke(vk, (mods & 0x01) != 0, c));
            }
        }
        return keys;
    }

    /// <summary>Types the text; waits up to 1.5 s for the user to release Ctrl/Alt/Win/Shift first (Busy otherwise).</summary>
    public static TypeResult Type(string text, int keyDelayMs)
    {
        var deadline = Environment.TickCount64 + 1500;
        while (ModifierHeld())
        {
            if (Environment.TickCount64 > deadline) return TypeResult.Busy;
            Thread.Sleep(30);
        }

        var hkl = GetKeyboardLayout(GetWindowThreadProcessId(GetForegroundWindow(), out _));
        var plan = Plan(text, c => VkKeyScanEx(c, hkl));
        foreach (var k in plan)
        {
            var inputs = new List<INPUT>(4);
            if (k.IsUnicode)
            {
                inputs.Add(Unicode(k.Unicode, false));
                inputs.Add(Unicode(k.Unicode, true));
            }
            else
            {
                if (k.Shift) inputs.Add(Key(VK_SHIFT, hkl, false));
                inputs.Add(Key(k.Vk, hkl, false));
                inputs.Add(Key(k.Vk, hkl, true));
                if (k.Shift) inputs.Add(Key(VK_SHIFT, hkl, true));
            }
            var arr = inputs.ToArray();
            if (SendInput((uint)arr.Length, arr, Marshal.SizeOf<INPUT>()) != arr.Length) return TypeResult.Blocked;
            if (keyDelayMs > 0) Thread.Sleep(keyDelayMs);
        }
        return TypeResult.Typed;
    }

    private static bool ModifierHeld() =>
        new[] { VK_SHIFT, VK_CONTROL, VK_MENU, VK_LWIN, VK_RWIN }.Any(vk => (GetAsyncKeyState(vk) & 0x8000) != 0);

    private static INPUT Key(ushort vk, IntPtr hkl, bool up) => new()
    {
        type = INPUT_KEYBOARD,
        U = new InputUnion
        {
            ki = new KEYBDINPUT
            {
                wVk = vk, wScan = (ushort)MapVirtualKeyEx(vk, MAPVK_VK_TO_VSC, hkl),
                dwFlags = up ? KEYEVENTF_KEYUP : 0,
            },
        },
    };

    private static INPUT Unicode(char c, bool up) => new()
    {
        type = INPUT_KEYBOARD,
        U = new InputUnion { ki = new KEYBDINPUT { wScan = c, dwFlags = KEYEVENTF_UNICODE | (up ? KEYEVENTF_KEYUP : 0) } },
    };

    private const uint INPUT_KEYBOARD = 1, KEYEVENTF_KEYUP = 0x0002, KEYEVENTF_UNICODE = 0x0004, MAPVK_VK_TO_VSC = 0;
    private const ushort VK_SHIFT = 0x10, VK_CONTROL = 0x11, VK_MENU = 0x12, VK_LWIN = 0x5B, VK_RWIN = 0x5C;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT { public uint type; public InputUnion U; }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }

    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] private static extern short VkKeyScanEx(char ch, IntPtr hkl);
    [DllImport("user32.dll")] private static extern uint MapVirtualKeyEx(uint code, uint mapType, IntPtr hkl);
    [DllImport("user32.dll")] private static extern IntPtr GetKeyboardLayout(uint threadId);
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    [DllImport("user32.dll")] private static extern short GetAsyncKeyState(int vk);
}
