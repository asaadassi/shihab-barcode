using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace BarcodeReceiver.Core;

/// <summary>This PC's IPv4 addresses on the networks a phone can reach (Wi-Fi, Ethernet, USB tethering).</summary>
internal static class NetInfo
{
    public static List<string> LocalIPv4()
    {
        var list = new List<(int Rank, string Ip)>();
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up) continue;
            if (ni.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;
            // Virtual switches (Hyper-V, WSL, VirtualBox) are not on the phone's network.
            var d = ni.Description;
            if (d.Contains("Hyper-V", StringComparison.OrdinalIgnoreCase) || d.Contains("VirtualBox", StringComparison.OrdinalIgnoreCase)
                || d.Contains("VMware", StringComparison.OrdinalIgnoreCase) || ni.Name.Contains("vEthernet", StringComparison.OrdinalIgnoreCase)) continue;
            var rank = ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? 0 : 1;
            foreach (var a in ni.GetIPProperties().UnicastAddresses)
            {
                if (a.Address.AddressFamily != AddressFamily.InterNetwork || IPAddress.IsLoopback(a.Address)) continue;
                var b = a.Address.GetAddressBytes();
                if (b[0] == 169 && b[1] == 254) continue;   // no DHCP answer: unreachable
                list.Add((rank, a.Address.ToString()));
            }
        }
        return list.OrderBy(x => x.Rank).Select(x => x.Ip).Distinct().Take(4).ToList();
    }
}
