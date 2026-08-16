# MateLinuxLauncher

MateLinuxLauncher, Android tabletlerde ARM64 Linux uygulamalarını tek tek ve cihaz odaklı profillerle çalıştırmak için geliştirilen deneysel bir Android uygulamasıdır.

İlk sürüm özellikle **Huawei MRDI-W09 / Kirin / Maleoon 920** cihaz profilini hedefler. Uygulama tam bir masaüstü emülatörü kurmak yerine Termux ve Termux:X11 katmanlarını denetler, güvenli bir çalışma ortamı probu yürütür ve cihazın grafik özelliklerine göre benchmark adayları üretir.

## İlk sürümde bulunanlar

- Android, bellek, ekran, OpenGL ES, Vulkan ve EGL/GPU profili
- Termux, Termux:X11 ve Shizuku kurulum denetimi
- Shizuku binder ve izin durumunun salt-okunur gösterimi
- Termux `RUN_COMMAND` üzerinden sabit, denetlenebilir çalışma ortamı probu
- Maleoon 920 için yazılımsal X11, VirGL + ANGLE Vulkan ve VirGL + ANGLE OpenGL adayları
- MRDI-W09 için ekonomik, dengeli, kalite ve doğal çözünürlük profilleri
- Son Termux sonucunu saklayan yaşam döngüsü dayanıklılığı
- Parser ve öneri motoru birim testleri
- GitHub Actions ile test, lint ve debug APK üretimi

## Güvenlik modeli

Uygulama kullanıcı tarafından yazılmış bir komutu shell'e göndermez. İlk probun metni uygulama içinde sabittir ve yalnızca mimari, mevcut komutlar ve ilgili paketlerin varlığı gibi teknik bilgileri döndürür. Seri numarası, hesap, IP adresi, dosya listesi veya kişisel içerik toplanmaz.

Shizuku ilk sürümde yalnızca durum/izin tespiti için kullanılır. Linux uygulamalarının çalışması Shizuku'ya bağlı değildir.

## Termux hazırlığı

1. Termux ve Termux:X11 aynı güvenilir dağıtım kaynağından kurulmalıdır.
2. Termux içinde `~/.termux/termux.properties` dosyasına şu satır eklenmelidir:

   ```properties
   allow-external-apps=true
   ```

3. Termux yeniden başlatılmalıdır.
4. Android uygulama ayarlarında MateLinuxLauncher için **Termux ortamında komut çalıştırma** ek izni verilmelidir.
5. Termux:X11'in Android uygulaması yanında Termux içindeki eşlik eden `termux-x11` paketi de kurulmalıdır.

## Yerel derleme

Proje Android Gradle Plugin 9.3.0, Gradle 9.5.0 ve JDK 17 ile yapılandırılmıştır. Depoda ikili Gradle wrapper dosyası tutulmamaktadır; CI sabitlenmiş Gradle sürümünü `gradle/actions/setup-gradle` üzerinden sağlar.

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

## Mevcut sınır

Bu ilk PR gerçek pencere veya 3D benchmark'ını henüz başlatmaz. Önce cihaz, paket ve renderer adaylarını güvenli biçimde doğrular. Sonraki aşama yalnızca doğrulanmış adaylar için süreli benchmark oturumları olacaktır.
