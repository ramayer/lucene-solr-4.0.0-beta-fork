/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.CharStream;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.solr.common.ResourceLoader;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.util.plugin.ResourceLoaderAware;

/**
 * Factory for {@link MappingCharFilter}. 
 * <pre class="prettyprint" >
 * &lt;fieldType name="text_map" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;charFilter class="solr.MappingCharFilterFactory" mapping="mapping.txt"/&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @version $Id$
 * @since Solr 1.4
 *
 */
public class MappingCharFilterFactory extends BaseCharFilterFactory implements
    ResourceLoaderAware {

  protected NormalizeCharMap normMap;
  private String mapping;

  public void inform(ResourceLoader loader) {
    mapping = args.get( "mapping" );

    if( mapping != null ){
      List<String> wlist = null;
      try{
        File mappingFile = new File( mapping );
        if( mappingFile.exists() ){
          wlist = loader.getLines( mapping );
        }
        else{
          List<String> files = StrUtils.splitFileNames( mapping );
          wlist = new ArrayList<String>();
          for( String file : files ){
            List<String> lines = loader.getLines( file.trim() );
            wlist.addAll( lines );
          }
        }
      }
      catch( IOException e ){
        throw new RuntimeException( e );
      }
      normMap = new NormalizeCharMap();
      parseRules( wlist, normMap );
    }
  }

  public CharStream create(CharStream input) {
    return new MappingCharFilter(normMap,input);
  }

  // "source" => "target"
  static Pattern p = Pattern.compile( "\"(.*)\"\\s*=>\\s*\"(.*)\"\\s*$" );

  protected void parseRules( List<String> rules, NormalizeCharMap normMap ){
    for( String rule : rules ){
      Matcher m = p.matcher( rule );
      if( !m.find() )
        throw new RuntimeException( "Invalid Mapping Rule : [" + rule + "], file = " + mapping );
      normMap.add( parseString( m.group( 1 ) ), parseString( m.group( 2 ) ) );
    }
  }

  char[] out = new char[256];
  
  protected String parseString( String s ){
    int readPos = 0;
    int len = s.length();
    int writePos = 0;
    while( readPos < len ){
      char c = s.charAt( readPos++ );
      if( c == '\\' ){
        if( readPos >= len )
          throw new RuntimeException( "Invalid escaped char in [" + s + "]" );
        c = s.charAt( readPos++ );
        switch( c ) {
          case '\\' : c = '\\'; break;
          case '"' : c = '"'; break;
          case 'n' : c = '\n'; break;
          case 't' : c = '\t'; break;
          case 'r' : c = '\r'; break;
          case 'b' : c = '\b'; break;
          case 'f' : c = '\f'; break;
          case 'u' :
            if( readPos + 3 >= len )
              throw new RuntimeException( "Invalid escaped char in [" + s + "]" );
            c = (char)Integer.parseInt( s.substring( readPos, readPos + 4 ), 16 );
            readPos += 4;
            break;
        }
      }
      out[writePos++] = c;
    }
    return new String( out, 0, writePos );
  }
}
