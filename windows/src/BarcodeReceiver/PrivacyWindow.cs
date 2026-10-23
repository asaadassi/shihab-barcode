using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace BarcodeReceiver;

/// <summary>Privacy and permissions: what the receiver keeps on this computer and what it sends (opened from About).</summary>
public sealed class PrivacyWindow : Window
{
    private static readonly string[] Paragraphs =
    [
        "يستقبل البرنامج الرموز التي تمسحها جوالاتك المقترنة به، ويكتبها في النافذة النشطة كما تفعل لوحة المفاتيح. لا يرسل الرموز إلى أي جهة.",
        "الاتصال: عبر الشبكة المحلية (واي فاي أو كبل) مشفّراً بـ TLS، ولا يقبل إلا جوالاً معه رمز الاقتران؛ أو عبر البلوتوث مع جوال اقترنته بنفسك في إعدادات ويندوز.",
        "يُحفظ على جهازك: شهادة هذا الحاسوب ورمز الاقتران مشفّرين بحماية ويندوز (DPAPI) فلا يقرؤهما إلا حسابك، والإعدادات، والسجلات (30 يوماً، دون الرموز الممسوحة)، في %LOCALAPPDATA%\\ShihabBarcodeReceiver. قائمة آخر الرموز على الشاشة في الذاكرة فقط وتُمسح عند الإغلاق.",
        "الإنترنت: يسأل GitHub عن نسخة جديدة، ويجلب ملف أخبار شهاب. لا يُرسل في الحالتين شيء عنك.",
        "الإبلاغ عن مشكلة ينشئ ملفاً مضغوطاً بالسجلات على جهازك، وأنت من يقرر إرساله.",
        "الصلاحيات: يعمل بصلاحيات المستخدم العادية. قد يسألك ويندوز أول مرة عن السماح له بالشبكة الخاصة، وذلك لازم للواي فاي فقط.",
        "إلغاء التثبيت يحذف كل ما سبق من الجهاز.",
    ];

    public PrivacyWindow()
    {
        Title = "الخصوصية";
        Width = 580; Height = 520; MinWidth = 420; MinHeight = 320;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        FlowDirection = FlowDirection.RightToLeft;
        Background = (Brush)FindResource("Bg");
        FontFamily = (FontFamily)FindResource("AppFont");
        var list = new StackPanel { Margin = new Thickness(24) };
        list.Children.Add(new TextBlock { Text = "الخصوصية", FontSize = 18, FontWeight = FontWeights.Bold, Foreground = (Brush)FindResource("Text"), Margin = new Thickness(0, 0, 0, 12) });
        foreach (var p in Paragraphs)
            list.Children.Add(new TextBlock { Text = p, TextWrapping = TextWrapping.Wrap, LineHeight = 22, FontSize = 13, Foreground = (Brush)FindResource("Text"), Margin = new Thickness(0, 0, 0, 10) });
        Content = new ScrollViewer { Content = list, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
    }
}
