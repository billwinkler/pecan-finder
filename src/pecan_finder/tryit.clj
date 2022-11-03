(ns pecan-finder.tryit
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [pecan-finder.quickstart :refer [annotate-image-with-obj-localization]])
  (:import  [java.nio.file Files Path Paths]))


(defn- jpg?
  "True if file extension is JPG"
  [file]
  (-> file .toString (str/split #"\.") last (= "JPG")))

(defn- file-name
  "Extract file name from java.io.File"
  [file]
  (when (jpg? file)
    (-> file .toString (str/split #"/") last)))

(defn list-resource-files
  "List files in a resource directory"
  [dir]
  (-> dir io/resource .toURI Paths/get .toFile file-seq rest))

(defn resource-images
  "file names of JPG images in resource/images directory"
  []
  (->> (map file-name (list-resource-files "images"))
       (remove nil?)))

(defn annotate-images!
  "Map image annotation over the images in `resources/images`"
  []
  (map annotate-image-with-obj-localization (resource-images)))

