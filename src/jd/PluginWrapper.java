//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.controlling.DownloadController;
import jd.controlling.JDLogger;
import jd.controlling.ProgressController;
import jd.nutils.JDFlags;
import jd.plugins.Plugin;
import jd.update.FileUpdate;
import jd.update.WebUpdater;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

/**
 * A Container for {@link jd.plugins.Plugin Plugins}. Plugins usually do not get
 * instantiated after programstart. {@link JDInit#initPlugins()} reads all
 * Annotations and creates a pluginwrapperinstance. The pluginwrapper creates a
 * plugininstance when required
 * 
 * @author unkown
 * 
 */
public abstract class PluginWrapper implements Comparable<PluginWrapper> {
    /**
     * Usage Flag for {@link jd.PluginWrapper.PluginWrapper(String, String,
     * String, String, int)}<br>
     */
    public static final int LOAD_ON_INIT = 1 << 1;
    /**
     * By default, a plugin pattern must macht a valid URL. Sometimes it is
     * required to match a javascript expression or anything like this. Use this
     * flag to tell the plugin that it should accept invalid URLs
     */
    public static final int PATTERN_ACCEPTS_INVALID_URI = 1 << 2;
    /**
     * By default, plugins can be disabled. But in some cases plugins should not
     * be disabled for controlling reasons. Use this flag to prevent the plugin
     * from disabeling
     */
    public static final int ALWAYS_ENABLED = 1 << 3;
    /**
     * See http://wiki.jdownloader.org/knowledge/wiki/glossary/cnl2 for cnl2
     * details. If a Decrypter uses CNL2, we can think about activating this
     * feature here. JDownloader then will only decrypt indriect or
     * deepencrypted links. Direct links will be opened in th systems
     * defaultbrowser to use CNL
     */
    public static final int CNL_2 = 1 << 4;
    /**
     * Load only if debug flag is set. For internal developer plugins
     */
    public static final int DEBUG_ONLY = 1 << 4;

    /**
     * The Regular expression pattern. This pattern defines which urls can be
     * handeled by this plugin
     */
    private Pattern pattern;
    /**
     * The domain od this plugin, which is the plugin's name, too
     */
    private String host;
    /**
     * Full qualified classname
     */
    private String className;
    /**
     * internal logger instance
     */
    protected Logger logger = jd.controlling.JDLogger.getLogger();
    /**
     * field to cache the plugininstance if it is loaded already
     */
    protected Plugin loadedPlugin = null;
    /**
     * @see PluginWrapper#PATTERN_ACCEPTS_INVALID_URI
     */
    private boolean acceptOnlyURIs = true;
    /**
     * @see PluginWrapper#ALWAYS_ENABLED
     */
    private boolean alwaysenabled = false;
    /**
     * Usage and InitFlags created by <br>{@link PluginWrapper#CNL_2} <br>
     * {@link PluginWrapper#DEBUG_ONLY} <br> {@link PluginWrapper#LOAD_ON_INIT} <br>
     * {@link PluginWrapper#PATTERN_ACCEPTS_INVALID_URI}
     */
    private int flags;
    /**
     * Static classloader. gets created when the first plugin should be
     * initiated.
     * 
     */
    private static URLClassLoader CL;

    /**
     * Static map where all pluginwrapper register themselves with key= {@link
     * {@link PluginWrapper#className}
     */
    private static final HashMap<String, PluginWrapper> WRAPPER = new HashMap<String, PluginWrapper>();

    /**
     * Creates a new wrapper
     * 
     * @param host
     *            domain of the plugin in lowercase
     * @param classNamePrefix
     *            package or null if classname is already fully qualifdied
     * @param classNameClassname
     *            of the plugin. or fully quialified Classpath
     * @param pattern
     *            {@link jd.PluginWrapper#pattern}
     * @param flags
     *            a integer wich has been created by <br>{@link PluginWrapper#CNL_2} <br>
     *            {@link PluginWrapper#DEBUG_ONLY} <br>
     *            {@link PluginWrapper#LOAD_ON_INIT} <br>
     *            {@link PluginWrapper#PATTERN_ACCEPTS_INVALID_URI}
     */
    public PluginWrapper(String host, String classNamePrefix, String className, String pattern, int flags) {
        String classn = (classNamePrefix == null ? "" : classNamePrefix) + className;
        if (pattern != null) {
            this.pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        }
        this.host = host.toLowerCase();
        this.className = classn;
        this.flags = flags;
        if (JDFlags.hasSomeFlags(flags, LOAD_ON_INIT)) this.getPlugin();
        if (JDFlags.hasSomeFlags(flags, ALWAYS_ENABLED)) this.alwaysenabled = true;
        if (JDFlags.hasSomeFlags(flags, PATTERN_ACCEPTS_INVALID_URI)) this.acceptOnlyURIs = false;
        if (JDFlags.hasNoFlags(flags, DEBUG_ONLY) || JDInitFlags.SWITCH_DEBUG) {

            WRAPPER.put(classn, this);
        }
    }

    /**
     * Should always return lifetime id. This means that this id never changes!
     * It is used as config key to save settings
     * 
     * @return
     */
    public String getID() {
        return getHost();
    }

    /**
     * instanciates the wrapped plugin. Tries to update the plugin before
     * loading. the new instance is cached, so the whol update process will only
     * be done once
     * 
     * @return plugin instance
     */
    public synchronized Plugin getPlugin() {
        if (loadedPlugin != null) return loadedPlugin;
        // is automated webupdate disabled?
        boolean manualupdate = SubConfiguration.getConfig("WEBUPDATE").getBooleanProperty(Configuration.PARAM_WEBUPDATE_DISABLE, false);
        try {

            if (CL == null) CL = new URLClassLoader(new URL[] { JDUtilities.getJDHomeDirectoryFromEnvironment().toURI().toURL(), JDUtilities.getResourceFile("java").toURI().toURL() }, Thread.currentThread().getContextClassLoader());
            // if webupdater's pluginlist is null (webupdater has run at least
            // once) check the hash before loading the plugin
            if (JDUtilities.getRunType() == JDUtilities.RUNTYPE_LOCAL_JARED && WebUpdater.getPluginList() != null) {

                // often plugins have inner classes. Here we find all classes
                // that belong to this plugin
                ArrayList<FileUpdate> filelist = new ArrayList<FileUpdate>();
                for (Entry<String, FileUpdate> entry : WebUpdater.PLUGIN_LIST.entrySet()) {
                    if (entry.getKey().startsWith("/" + getClassName().replace(".", "/"))) {
                        filelist.add(entry.getValue());
                    }
                }

                ProgressController progress = new ProgressController(JDL.LF("wrapper.webupdate.updateFile", "Update plugin %s", getClassName()), filelist.size() + 1);
                progress.increase(1);

                for (FileUpdate entry : filelist) {

                    String plg = entry.getLocalPath();
                    // if plugin is out of date (hashes do not equal)
                    if (!entry.equals()) {
                        // and autoupdate is enabled
                        if (!manualupdate) {
                            // update the file
                            if (!new WebUpdater().updateUpdatefile(entry)) {
                                logger.severe("Could not update plugin: " + plg);
                            } else {
                                logger.info("Updated plugin: " + plg);
                            }
                        } else {
                            logger.info("New plugin: " + plg + " available, but update-on-the-fly is disabled!");
                        }
                    }
                    progress.increase(1);
                }
                progress.doFinalize();
            }
            logger.finer("load plugin: " + getClassName());

            Class<?> plgClass;

            try {
                plgClass = CL.loadClass(getClassName());
            } catch (ClassNotFoundException e) {
                // fallback classloader.
                logger.severe("Fallback cloassloader used");
                plgClass = JDUtilities.getJDClassLoader().loadClass(getClassName());
            }

            if (plgClass == null) {
                logger.severe("PLUGIN " + this.getClassName() + "NOT FOUND!");
                return null;
            }
            Class<?>[] classes = new Class[] { PluginWrapper.class };
            Constructor<?> con = plgClass.getConstructor(classes);
            classes = null;
            this.loadedPlugin = (Plugin) con.newInstance(new Object[] { this });

            return loadedPlugin;
        } catch (Exception e) {
            logger.severe("Plugin Exception!");
            JDLogger.exception(e);
        }
        return null;
    }

    /**
     * Returns the VersionID for this plugin. it may be only available after
     * loading the plugin. BUT to date, revisions are autoset in the annotations
     * and will return the correct version without loading the plugin
     * 
     * @return
     */
    abstract public String getVersion();

    /**
     * 
     * @return "idle" if the plugin has not been loaded or
     *         {@link jd.plugins.Plugin#getCoder()}
     */
    public String getCoder() {
        return loadedPlugin != null ? loadedPlugin.getCoder() : JDL.L("plugin.system.notloaded", "idle");
    }

    /**
     * if {@link #alwaysenabled} is enabled this method will return true. Else
     * the plugin config is used. By default, plugins are enabled.
     * 
     * @return
     */
    public boolean isEnabled() {
        return this.alwaysenabled || getPluginConfig().getBooleanProperty("USE_PLUGIN", true);
    }

    /**
     * if {@link #alwaysenabled} is enabled, this method will be ignored, else
     * the plugin can be enabled or disabled here
     * 
     * @param bool
     */
    public void setEnabled(boolean bool) {
        if (this.alwaysenabled) return;
        getPluginConfig().setProperty("USE_PLUGIN", bool);
        getPluginConfig().save();
        if (JDUtilities.getController() != null) DownloadController.getInstance().fireGlobalUpdate();
    }

    /**
     * 
     * @param data
     *            any stringdata
     * @return true if data contains a match to {@link #pattern}
     */
    public boolean canHandle(String data) {
        if (this.isLoaded()) { return getPlugin().canHandle(data); }
        if (data == null) { return false; }
        Pattern pattern = this.getPattern();
        if (pattern != null) {
            Matcher matcher = pattern.matcher(data);
            if (matcher.find()) { return true; }
        }
        return false;
    }

    /**
     * 
     * @return true if the plugin is already loaded
     * @see #getPlugin()
     */
    public boolean isLoaded() {
        return this.loadedPlugin != null;
    }

    /**
     * Returns the plugins Subconfiguration
     * 
     * @return
     */

    public SubConfiguration getPluginConfig() {
        return SubConfiguration.getConfig(getHost());
    }

    /**
     * Creates a NEW instance of the plugin
     * 
     * @return
     */

    public Plugin getNewPluginInstance() {
        try {
            return getPlugin().getClass().getConstructor(new Class[] { PluginWrapper.class }).newInstance(new Object[] { this });
        } catch (Exception e) {
            JDLogger.exception(e);
        }
        return null;
    }

    /**
     * 
     * @return true if the plugin is already {@link #isLoaded() loaded} and
     *         there are {@link jd.config.ConfigEntry}s defined.
     */
    public boolean hasConfig() {
        return isLoaded() && !getPlugin().getConfig().getEntries().isEmpty();
    }

    /**
     * Delegates the compareTo functionality to hostA.compareTo(hostB)
     */
    public int compareTo(PluginWrapper plg) {
        return getHost().toLowerCase().compareTo(plg.getHost().toLowerCase());
    }

    /**
     * The name of the config. This should be unique for the plugin!
     * 
     * @return
     */
    public String getConfigName() {
        return getHost();
    }

    /**
     * Static getter. All pluginwrapper are cached in {@link #WRAPPER}
     * 
     * @param clazz
     *            fully qualified {@link #className}
     * @return
     */
    public static PluginWrapper getWrapper(String clazz) {
        return WRAPPER.get(clazz);
    }

    /**
     * Creates a new instance of the plugin with classname
     * 
     * @param className
     *            ully qualified {@link #className}
     * @return
     */
    public static Plugin getNewInstance(String className) {
        if (!WRAPPER.containsKey(className)) {
            try {
                throw new Exception("plugin " + className + " could not be found");
            } catch (Exception e) {
                JDLogger.exception(e);
                return null;
            }
        }
        return WRAPPER.get(className).getNewPluginInstance();
    }

    /**
     * @return the {@link PluginWrapper#pattern}
     * @see PluginWrapper#pattern
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * @return the {@link PluginWrapper#host}
     * @see PluginWrapper#host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the {@link PluginWrapper#alwaysenabled}
     * @see PluginWrapper#alwaysenabled
     */
    public boolean isAlwaysenabled() {
        return alwaysenabled;
    }

    /**
     * @return the {@link PluginWrapper#flags}
     * @see PluginWrapper#flags
     */
    public int getFlags() {
        return flags;
    }

    /**
     * @return the {@link PluginWrapper#className}
     * @see PluginWrapper#className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return the {@link PluginWrapper#acceptOnlyURIs}
     * @see PluginWrapper#acceptOnlyURIs
     */
    public boolean isAcceptOnlyURIs() {
        return acceptOnlyURIs;
    }

}
