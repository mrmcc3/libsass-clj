(ns libsass.build
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [wrm.libsass SassCompiler
                        SassCompiler$OutputStyle
                        SassCompiler$InputSyntax]))

(defn opts->style
  "inspects the compiler options and returns the libsass output style"
  [opts]
  (case (:style opts)
    :compact SassCompiler$OutputStyle/compact
    :compressed SassCompiler$OutputStyle/compressed
    SassCompiler$OutputStyle/expanded))

(defn opts->compiler
  "given a map of compiler options create and setup the libsass copmiler"
  [opts]
  (doto (SassCompiler.)
    (.setEmbedSourceMapInCSS (get opts :source-map false))
    (.setEmbedSourceContentsInSourceMap false)
    (.setGenerateSourceComments false)
    (.setGenerateSourceMap (get opts :source-map false))
    (.setIncludePaths nil)
    (.setOmitSourceMappingURL false)
    (.setOutputStyle (opts->style opts))
    (.setPrecision (or (:precision opts) 10))))

(defn input-ext
  "given a [input-path output-path] pair return a keyword representing
  the input style based on the input-path extension"
  [[input-path _]]
  (cond
    (.endsWith input-path ".scss") :scss
    (.endsWith input-path ".sass") :sass
    :else nil))

(defn compile!
  "given a compiler and in/out paths perform compilation"
  [compiler in out]
  (io/make-parents out)
  (spit out (-> compiler (.compileFile in out out) .getCssOutput)))

(defn build-files!
  "paths is a map of input sass/scss absolute paths to output css
  absolute paths. opts is the map of compiler options.
  will invoke the libsass compiler to produce the css files"
  [paths opts]
  (cond
    (empty? paths)
    nil
    (apply distinct? (vals paths))
    (let [compiler (opts->compiler opts)
          {:keys [scss sass]} (group-by input-ext paths)]
      (when scss
        (.setInputSyntax compiler SassCompiler$InputSyntax/scss))
      (doseq [[in out] scss]
        (compile! compiler in out))
      (when sass
        (.setInputSyntax compiler SassCompiler$InputSyntax/sass))
      (doseq [[in out] sass]
        (compile! compiler in out)))
    :else
    (throw (Exception. "libsass: output files not distinct"))))

(defn relativize
  "find a relative path between a file path parent directory path."
  [parent-path file-path]
  (-> (io/file parent-path)
      .toURI
      (.relativize (-> file-path io/file .toURI))
      .getPath))

(defn sass-entry-file?
  "checks if the given file has a scss/sass extension
  and doesn't start with an undescore."
  [f]
  (let [name (.getName f)]
    (and (.isFile f)
         (or (.endsWith name ".scss")
             (.endsWith name ".sass"))
         (not (.startsWith name "_")))))

(defn dir->paths
  "create a map of sass/scss input file paths to output file paths for
  the given source-path and output-path"
  [source-path output-path]
  (->> (io/file source-path)
       file-seq
       (filter sass-entry-file?)
       (map
         (fn [in-file]
           (let [in-path (.getAbsolutePath in-file)
                 rel-path (relativize source-path in-file)
                 out-file (io/file output-path rel-path)
                 out-path (str/replace-first
                            (.getAbsolutePath out-file)
                            #"\.(scss|sass)$"
                            ".css")]
             [in-path out-path])))
       (into {})))

(defn build
  "build all sass/scss files in source-paths. this function constructs
  the map of input file paths to output file paths and calls build-files!"
  [source-paths {:keys [output-dir] :as opts}]
  (let [reducer (fn [paths source-path]
                  (merge paths (dir->paths source-path output-dir)))]
    (build-files! (reduce reducer {} source-paths) opts)))



(comment

  (relativize "scss" "scss/a/b/test.scss")

  (dir->paths "scss" "public/compiled-css")
  (build-files! *1 {})

  (build ["scss"] {:output-dir "public/compiled-css"
                   :source-map true
                   :style      :compressed}))
