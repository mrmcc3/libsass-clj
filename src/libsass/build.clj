(ns libsass.build
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hawk.core :as hawk])
  (:import [io.bit3.jsass Options OutputStyle]))

(defn opts->jsass
  "given a map of compiler options construct jsass options"
  [{:keys [source-map source-comments output-style precision]}]
  (let [opts (Options.)]
    (cond
      (nil? source-map)
      (do
        (.setSourceMapContents opts false)
        (.setSourceMapEmbed opts false)
        (.setOmitSourceMapUrl opts true))
      (= source-map :inline)
      (do
        (.setSourceMapContents opts false)
        (.setSourceMapEmbed opts true)
        (.setOmitSourceMapUrl opts false))
      :else
      (println "source-map option is unknown or not yet implemented."))
    (when precision
      (.setPrecision opts precision))
    (when source-comments
      (.setSourceComments opts true))
    (.setOutputStyle
      opts
      (case output-style
        :nested OutputStyle/NESTED
        :compact OutputStyle/COMPACT
        :compressed OutputStyle/COMPRESSED
        OutputStyle/EXPANDED))
    opts))

(def jsass-compiler (io.bit3.jsass.Compiler.))

(defn compile!
  "given in/out paths and options use the jsass compiler to produce the output"
  [in-path out-path jsass-opts]
  (io/make-parents out-path)
  (let [output (.compileFile
                 jsass-compiler
                 (-> in-path io/file .toURI)
                 (-> out-path io/file .toURI)
                 jsass-opts)]
    (spit out-path (.getCss output))))

(defn input-ext
  "given a [input-path output-path] pair return a keyword representing
  the input style based on the input-path extension"
  [[input-path _]]
  (cond
    (.endsWith input-path ".scss") :scss
    (.endsWith input-path ".sass") :sass
    :else nil))

(defn build-files!
  "paths is a map of input sass/scss absolute paths to output css
  absolute paths. opts is the map of compiler options.
  will invoke the libsass compiler to produce the css files"
  [paths opts]
  (cond
    (empty? paths)
    nil
    (apply distinct? (vals paths))
    (let [jsass-opts (opts->jsass opts)
          {:keys [scss sass]} (group-by input-ext paths)]
      (when scss
        (.setIsIndentedSyntaxSrc jsass-opts false))
      (doseq [[in-path out-path] scss]
        (compile! in-path out-path jsass-opts))
      (when sass
        (.setIsIndentedSyntaxSrc jsass-opts true))
      (doseq [[in-path out-path] sass]
        (compile! in-path out-path jsass-opts)))
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

(defn clean-empty-dirs
  "recursively attempts to delete empty directories"
  [root]
  (when (.isDirectory root)
    (doseq [child-file (.listFiles root)]
      (clean-empty-dirs child-file))
    (io/delete-file root true)))

(defn clean
  "remove all build artifacts if they exist. this function first finds
  all the output paths then safely removes them."
  [source-paths {:keys [output-dir]}]
  (let [reducer (fn [paths source-path]
                  (merge paths (dir->paths output-dir source-path)))
        output-paths (vals (reduce reducer {} source-paths))]
    (doseq [path (set output-paths)]
      (when (.exists (io/file path))
        (io/delete-file path)))
    (clean-empty-dirs (io/file output-dir))))

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

  (build ["scss"] {:output-dir   "public/compiled-css"
                   :output-style :expanded
                   :source-map   :inline})

  (clean ["scss"] {:output-dir "public/compiled-css"})

  (watch ["scss"] {:output-dir "public/compiled-css"
                   :source-map :inline})
  (*1)

  )
