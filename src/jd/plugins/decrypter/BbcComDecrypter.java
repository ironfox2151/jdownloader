//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "bbc.com" }, urls = { "https?://(?:www\\.)?(bbc\\.com|bbc\\.co\\.uk)/.+" })
public class BbcComDecrypter extends PluginForDecrypt {

    public BbcComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    @SuppressWarnings("unchecked")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        String[] jsons = this.br.getRegex("data\\-playable=\"(.*?)\">").getColumn(0);
        if (jsons != null && jsons.length != 0) {
            jsons[0] = Encoding.htmlDecode(jsons[0]);
        }
        if (jsons == null || jsons.length == 0) {
            /* Type 1 */
            jsons = this.br.getRegex("data\\-playable=\\'(.*?)\\'>").getColumn(0);
        }
        if (jsons == null || jsons.length == 0) {
            /* Type 2 */
            jsons = this.br.getRegex("playlistObject\\s*?:\\s*?(\\{.*?\\}),[\n]+").getColumn(0);
        }
        if (jsons == null || jsons.length == 0) {
            /* Type 3 */
            jsons = this.br.getRegex("_exposedData=(\\{.+),").getColumn(0);
        }
        if (jsons == null) {
            logger.info("Failed to find any playable content");
            return decryptedLinks;
        }
        LinkedHashMap<String, Object> entries = null;
        for (String json : jsons) {
            if (json.contains("{&quot;")) {
                json = Encoding.htmlDecode(json);
            }
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
            final Object story = entries.get("story");
            String title = null;
            String description = null;
            String vpid = null;
            if (story != null) {
                /* Type 3 */
                entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "story/Content/AssetVideoIb2/{0}");
                if (entries == null) {
                    logger.info("Failed to find video content");
                    break;
                }
                title = (String) entries.get("Title");
                vpid = (String) entries.get("Vpid");
            } else {
                /* Type 1 */
                Object sourcemapo = JavaScriptEngineFactory.walkJson(entries, "settings/playlistObject");
                if (sourcemapo == null) {
                    /* Type 2 */
                    sourcemapo = JavaScriptEngineFactory.walkJson(entries, "allAvailableVersions/{0}/smpConfig");
                }
                entries = (LinkedHashMap<String, Object>) sourcemapo;
                if (entries == null) {
                    logger.info("Failed to find video content");
                    break;
                }
                title = (String) entries.get("title");
                description = (String) entries.get("summary");
                entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "items/{0}");
                vpid = (String) entries.get("vpid");
            }
            if (inValidate(title) || inValidate(vpid)) {
                continue;
            }

            title = encodeUnicode(title);

            final DownloadLink dl = createDownloadlink("http://bbcdecrypted/" + vpid);
            dl.setLinkID(vpid);
            dl.setName(title + ".mp4");
            dl.setProperty("decrypterfilename", title);
            dl.setContentUrl(parameter);

            if (!inValidate(description)) {
                dl.setComment(description);
            }

            decryptedLinks.add(dl);
        }

        if (decryptedLinks.size() == 0) {
            logger.info("Failed to find any playable content --> Probably only irrelevant photo content or no content at all --> Adding offline url");
            decryptedLinks.add(this.createOfflinelink(parameter));
        }

        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }

        return decryptedLinks;
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    protected boolean inValidate(final String s) {
        if (s == null || s.matches("\\s+") || s.equals("")) {
            return true;
        } else {
            return false;
        }
    }

}
