(ns libsass.build
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hawk.core :as hawk])
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

(defn sass-file?
  "checks if the given file has a scss/sass extension."
  [f]
  (and (not (.isDirectory f))
       (or (.endsWith (.getName f) ".scss")
           (.endsWith (.getName f) ".sass"))))

(defn sass-entry-file?
  "checks if the given file has a scss/sass extension
  and doesn't start with an undescore."
  [f]
  (and (sass-file? f)
       (not (.startsWith (.getName f) "_"))))

(defn file->paths
  "given a source-path, output-path and a source file calculate the
  absolute [input-path output-path] pair."
  [output-path source-path file]
  (let [in-path (.getAbsolutePath file)
        rel-path (relativize source-path file)
        out-file (io/file output-path rel-path)
        out-path (str/replace-first
                   (.getAbsolutePath out-file)
                   #"\.(scss|sass)$"
                   ".css")]
    [in-path out-path]))

(defn dir->paths
  "create a map of sass/scss input file paths to output file paths for
  the given source-path and output-path"
  [output-path source-path]
  (->> (io/file source-path)
       file-seq
       (filter sass-entry-file?)
       (map (partial file->paths output-path source-path))
       (into {})))

(defn build
  "build all sass/scss files in source-paths. this function constructs
  the map of input file paths to output file paths and calls build-files!"
  [source-paths {:keys [output-dir] :as opts}]
  (let [reducer (fn [paths source-path]
                  (merge paths (dir->paths output-dir source-path)))]
    (build-files! (reduce reducer {} source-paths) opts)))

(defn clean
  "remove all build artifacts if they exist. this function first finds
  all the output paths then safely removes them."
  [source-paths {:keys [output-dir]}]
  (let [reducer (fn [paths source-path]
                  (merge paths (dir->paths output-dir source-path)))
        output-paths (vals (reduce reducer {} source-paths))]
    ;; remove files
    (doseq [path (set output-paths)]
      (when (.exists (io/file path))
        (io/delete-file path)))
    ;; remove any empty directories
    (doseq [f (file-seq (io/file output-dir))]
      (when (.isDirectory f)
        (io/delete-file f true)))
    (io/delete-file output-dir true)))

(defn watch
  "watch source-paths for file changes and recompile.
  On create/modify: without inspecting the import statements of all the
  sass source files we have no way of knowing which sass entry files to
  recompile. So we are left with no option but to rebuild everything.
  On delete: if it's an entry file then remove the generated file.
  watch returns a no argument function that stops the hawk watcher."
  [source-paths {:keys [output-dir] :as opts}]
  (let [delete-filter (fn [_ {:keys [kind file]}]
                        (and (= kind :delete) (sass-entry-file? file)))
        delete-spec (fn [source-path]
                      {:paths   [source-path]
                       :filter  delete-filter
                       :handler (fn [ctx {:keys [file]}]
                                  (let [[_ out-path] (file->paths output-dir
                                                                  source-path
                                                                  file)]
                                    (.delete (io/file out-path)))
                                  ctx)})
        delete-specs (into [] (map delete-spec) source-paths)
        update-filter (fn [_ {:keys [kind file]}]
                        (and (not= kind :delete) (sass-file? file)))
        update-handler (fn [ctx _] (build source-paths opts) ctx)
        watcher (hawk/watch! (conj delete-specs
                                   {:paths   source-paths
                                    :filter  update-filter
                                    :handler update-handler}))]
    (fn [] (hawk/stop! watcher) nil)))

;; NOTE: if full rebuilds make the watcher too slow to be useful then it might
;; be worth considering a minimal sass version of clojure.tools.namespace.
;; for example https://github.com/xzyfer/sass-graph

(comment

  (relativize "scss" "scss/a/b/test.scss")

  (dir->paths "public/compiled-css" "scss")

  (build-files! *1 {})

  (build ["scss"] {:output-dir "public/compiled-css"
                   :style      :compressed})

  (clean ["scss"] {:output-dir "public/compiled-css"})

  (watch ["scss"] {:output-dir "public/compiled-css"
                   :source-map true})
  (*1)

  )
