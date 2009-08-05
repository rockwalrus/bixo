/*
 * Copyright (c) 1997-2009 101tec Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights 
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
 * copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package bixo.fetcher;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;

import org.apache.hadoop.fs.FileUtil;
import org.junit.Test;

import bixo.config.FakeUserFetcherPolicy;
import bixo.datum.FetchedDatum;
import bixo.datum.UrlDatum;
import bixo.fetcher.http.SimpleHttpFetcher;
import bixo.fetcher.http.IHttpFetcher;
import bixo.fetcher.util.LastFetchScoreGenerator;
import bixo.fetcher.util.PLDGrouping;
import bixo.pipes.FetchPipe;
import bixo.urldb.IUrlNormalizer;
import bixo.urldb.UrlImporter;
import bixo.urldb.UrlNormalizer;
import bixo.utils.TimeStampUtil;
import cascading.flow.Flow;
import cascading.flow.FlowConnector;
import cascading.pipe.Pipe;
import cascading.scheme.SequenceFile;
import cascading.tap.Lfs;
import cascading.tuple.Fields;
import cascading.tuple.TupleEntry;
import cascading.tuple.TupleEntryIterator;

public class FetcherTest {
    private static final long TEN_DAYS = 1000 * 60 * 60 * 24 * 10;

    private static final String USER_AGENT = "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.0.2) Gecko/2008092313 Ubuntu/8.04 (hardy) Firefox/3.1.6";
    
    private String makeUrlDB(String workingFolder, String inputPath) throws IOException {

        // We don't want to regenerate this DB all the time.
        if (!new File(workingFolder, UrlImporter.URL_DB_NAME).exists()) {
            UrlImporter urlImporter = new UrlImporter();
            FileUtil.fullyDelete(new File(workingFolder));
            urlImporter.importUrls(inputPath, workingFolder);
        }

        return workingFolder + "/" + UrlImporter.URL_DB_NAME;
    }
    
    @Test
    public void testStaleConnection() throws Exception {
        String workingFolder = "build/test-data/FetcherTest-stale/working";
        String inputPath = makeUrlDB(workingFolder, "src/test-data/facebook-artists.txt");
        Lfs in = new Lfs(new SequenceFile(UrlDatum.FIELDS), inputPath, true);
        String outPath = workingFolder + "/" + "FetcherTest" + TimeStampUtil.nowWithUnderLine();
        Lfs out = new Lfs(new SequenceFile(Fields.ALL), outPath, true);

        Pipe pipe = new Pipe("urlSource");

        IUrlNormalizer urlNormalizer = new UrlNormalizer();
        PLDGrouping grouping = new PLDGrouping();
        LastFetchScoreGenerator scoring = new LastFetchScoreGenerator(System.currentTimeMillis(), TEN_DAYS);
        
        IHttpFetcher fetcher = new SimpleHttpFetcher(10, new FakeUserFetcherPolicy(5), USER_AGENT);
        FetchPipe fetchPipe = new FetchPipe(pipe, urlNormalizer, grouping, scoring, fetcher);

        FlowConnector flowConnector = new FlowConnector();

        Flow flow = flowConnector.connect(in, out, fetchPipe);
        flow.complete();
    }

    @Test
    public void testRunFetcher() throws Exception {
        String workingFolder = "build/test-data/FetcherTest-run/working";
        String inputPath = makeUrlDB(workingFolder, "src/test-data/top10urls.txt");
        Lfs in = new Lfs(new SequenceFile(UrlDatum.FIELDS), inputPath, true);
        String outPath = workingFolder + "/" + "FetcherTest" + TimeStampUtil.nowWithUnderLine();
        Lfs out = new Lfs(new SequenceFile(FetchedDatum.FIELDS), outPath, true);

        Pipe pipe = new Pipe("urlSource");

        IUrlNormalizer urlNormalizer = new UrlNormalizer();
        PLDGrouping grouping = new PLDGrouping();
        LastFetchScoreGenerator scoring = new LastFetchScoreGenerator(System.currentTimeMillis(), TEN_DAYS);
        
        IHttpFetcher fetcher = new SimpleHttpFetcher(10, USER_AGENT);
        FetchPipe fetchPipe = new FetchPipe(pipe, urlNormalizer, grouping, scoring, fetcher);

        FlowConnector flowConnector = new FlowConnector();

        Flow flow = flowConnector.connect(in, out, fetchPipe);
        flow.complete();
        
        // Test for 10 good fetches.
        Fields metaDataFields = new Fields();
        int fetchedPages = 0;
        TupleEntryIterator openSink = flow.openSink();
        while (openSink.hasNext()) {
            TupleEntry entry = openSink.next();
            FetchedDatum datum = new FetchedDatum(entry, metaDataFields);
            Assert.assertEquals(200, datum.getHttpStatus());
            fetchedPages += 1;
        }

        Assert.assertEquals(10, fetchedPages);
    }
    
}
