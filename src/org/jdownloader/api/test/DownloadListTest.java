package org.jdownloader.api.test;

import java.util.List;

import org.appwork.storage.Storage;
import org.jdownloader.api.test.TestClient.Test;
import org.jdownloader.myjdownloader.client.AbstractMyJDClientForDesktopJVM;
import org.jdownloader.myjdownloader.client.bindings.downloadlist.DownloadPackageQuery;
import org.jdownloader.myjdownloader.client.bindings.downloadlist.DownloadPackageStorable;
import org.jdownloader.myjdownloader.client.bindings.interfaces.ContentInterface;
import org.jdownloader.myjdownloader.client.bindings.interfaces.DownloadsListInterface;

public class DownloadListTest extends Test {

    @Override
    public void run(Storage config, AbstractMyJDClientForDesktopJVM api) throws Exception {
        String dev;
        DownloadsListInterface link = api.link(DownloadsListInterface.class, dev = chooseDevice(api));

        DownloadPackageQuery pq = new DownloadPackageQuery();
        pq.setStatus(true);

        List<DownloadPackageStorable> packages = link.queryPackages(pq);

        byte[] ico = api.link(ContentInterface.class, dev).getIcon(packages.get(0).getStatusIconKey(), 32);
        // List<DownloadLinkStorable> smallList = link.queryLinks(new DownloadLinkQuery());
        // DownloadLinkQuery query = new DownloadLinkQuery();
        // query.setBytesLoaded(true);
        // query.setBytesTotal(true);
        // query.setEnabled(true);
        // query.setEta(true);
        // query.setExtractionStatus(true);
        // query.setFinished(true);
        // query.setHost(true);
        // query.setRunning(true);
        // query.setSkipped(true);
        // query.setSpeed(true);
        // query.setStatus(true);
        // // query.setUrl(true);
        // query.setPriority(true);
        // List<DownloadLinkStorable> bigList = link.queryLinks(query);

        // link.setPriority(PriorityStorable.HIGHEST, new long[] { 1400828251836l }, null);
        // DownloadPackageQuery pq = new DownloadPackageQuery();
        // pq.setHosts(true);
        // link.queryPackages(pq);

        System.out.println(1);
    }
}
