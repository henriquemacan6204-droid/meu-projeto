# Luma Glass — navegador Android inspirado no Safari iOS 27

Projeto nativo Android com **GeckoView 158**; interface mobile arredondada e translúcida, abas, modo privativo, Favoritos, Histórico, endereço/busca, compartilhamento, modo de identificação iPhone 18 Pro/iOS 27, proteção antirrastreamento, extensão AdBlock local e instalação/gerenciamento de extensões .xpi assinadas pela Mozilla.

## Baixar APK
Abra **Actions > Compilar Luma Glass APK > execução verde > Artifacts > LumaGlass-iOS27-Gecko-APK**. Extraia o ZIP e instale o app-debug.apk. APK debug assinado automaticamente pelo Android SDK, não versão de produção.

## Compilar
Gradle 8.9, Java 21, Android SDK platform 35, AGP 8.7.3. A dependência GeckoView é baixada de https://maven.mozilla.org/maven2.
Execute: gradle :app:assembleDebug

## O que funciona / o que ainda falta
- Navegação real em páginas da web com motor GeckoView.
- Identificação iPhone troca User-Agent e viewport; **não muda o sistema operacional**, nem o motor para o WebKit. Sites podem detectar Android; serviços como Instagram podem exibir versões diferentes.
- Bloqueio: Tracking Protection do GeckoView e extensão MV2 integrada com lista de domínios. Não é equivalente a todos os filtros do uBlock Origin.
- Extensões: gerenciamento e instalação por URL HTTPS direta de arquivo .xpi compatível e assinado pela Mozilla. Algumas extensões não funcionam em GeckoView; catálogo AMO é aberto para descoberta.
- VPN: botão abre a configuração de VPN do Android. **Não é uma VPN própria**: para mascarar IP é necessário um provedor externo ou implementar um túnel seguro com servidor.
- Este protótipo não inclui permissões de câmera/microfone, importação de extensões Chrome, downloads avançados, sincronização, grupos automáticos com IA ou integração com Apple. A UI é inspirada, não usa código nem assets da Apple.
- Não houve teste em aparelho físico nesta execução; confirme a compilação no GitHub Actions antes de instalar.

## Fontes técnicas
- https://www.apple.com/br/os/ios/
- https://support.apple.com/en-ie/guide/iphone/ipha9ffea1a3/27/ios/27
- https://mozilla.github.io/geckoview/consumer/docs/web-extensions
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html

Licenciamento: GeckoView e Gecko são licenciados pela MPL 2.0; mantenha avisos e licenças das dependências. O design Luma Glass é independente da Apple.
