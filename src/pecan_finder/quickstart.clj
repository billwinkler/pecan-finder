(ns pecan-finder.quickstart
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [pecan-finder.image :as img])
  (:import [com.google.cloud.vision.v1 AnnotateImageRequest
            AnnotateImageResponse BatchAnnotateImagesResponse EntityAnnotation
            Feature Feature$Type Image ImageAnnotatorClient]
           [com.google.protobuf ByteString Descriptors Descriptors$FieldDescriptor]
           [java.nio.file Files Path Paths]
           [java.util ArrayList List]
           [java.net URI]
           [javax.imageio ImageIO]
           [java.awt Color]
           [java.awt.image BufferedImage]))


(def file-name "20200901_120215.JPG") ;; pecans in grass
(def file-name "20200901_120244.JPG") ;; pecans on pavement
(def file-name "20200901_120253.JPG") ;; "animal"

;; the client can be reused
(def client (ImageAnnotatorClient/create))

(defn load-image
  "Read an image file into memory"
  [filename]
  (-> (str "images/" filename)
      io/resource
      .toURI
      Paths/get
      ;; get the image bytes
      Files/readAllBytes
      ByteString/copyFrom))

(def feature-types 
  {:label-detection Feature$Type/LABEL_DETECTION
   :crop-hints Feature$Type/CROP_HINTS
   :obj-localization Feature$Type/OBJECT_LOCALIZATION})

(defn build-request
  "Build an image annotation request"
  [filename feature-type]
  (let [img (-> (Image/newBuilder)
                (.setContent (load-image filename))
                (.build))
        feat (-> (Feature/newBuilder)
                 (.setType (feature-types feature-type))
                 (.build))]
    (-> (AnnotateImageRequest/newBuilder)
        (.addFeatures feat)
        (.setImage img)
        (.build))))

(defn print-annotations
  "Iterate over response elements and print them"
  [responses]
  (for [x (->> responses .iterator iterator-seq)]
    (for [y  (.getLabelAnnotationsList x)]
      (doseq [[k v] (.getAllFields y)]
        (println (str (.getName k) " " v))))))

(defn responses-as-maps
  "Collect response elements into a set of maps"
  [responses]
  
  (for [x (->> responses .iterator iterator-seq)]
    (for [y  (.getLabelAnnotationsList x)]
      (apply merge
            (for [[k v] (.getAllFields y)]
              {(-> k .getName keyword) v})))))

(defn annotate-image-as-maps
  "Submit the image annotation request"
  [filename]
  (let [request (build-request filename :label-detection)
        requests (doto (new ArrayList) (.add request))]
    (-> (.batchAnnotateImages client requests)
        (.getResponsesList)
        responses-as-maps)))

(defn magick-crop-dims

  "create the image magick crop command-line dimensions for an image
  as in: convert -crop 1693x3023+2419+0 20200901_120244.JPG cropped.JPG"
  [vertices]
  (let [[p1 p2 p3 p4] vertices
        width (- (:x p2) (:x p1))
        height (- (:y p3) (:y p2))]
    (format "%sx%s+%s+%s" width height (:x p1) (:y p1))))

(defn normalized-magick-crop-dims
  "create the image magick crop command-line dimensions for an image
  as in: convert -crop 1693x3023+2419+0 20200901_120244.JPG cropped.JPG"
  [filename vertices]
  (let [[p1 p2 p3 p4] vertices
        {image-width :width image-height :height} (img/image-size filename)
        width (* image-width (- (:x p2) (:x p1)))
        height (* image-height (- (:y p3) (:y p2)))]
    (format "%.1fx%.1f+%.1f+%.1f" width height (* image-width (:x p1)) (* image-height (:y p1)))))

(defn parse-crop-hints
  "Extract vertices from crop hint annotation responses"
  [responses]
  (for [resp (->> responses .iterator iterator-seq)]
    (let [ann (.getCropHintsAnnotation resp)]
      (for [hint (.getCropHintsList ann)]
        (let [verts (for [v (-> hint .getBoundingPoly .getVerticesList)]
                      {:x (.getX v) :y (.getY v)})]
          {:importance (.getImportanceFraction hint)
           :conf (.getConfidence hint)
           :vertices verts
           :crop (magick-crop-dims verts)})))))

(defn annotate-image-with-crop-hints
  "Submit the image annotation request"
  [filename]
  (let [request (build-request filename :crop-hints)
        requests (doto (new ArrayList) (.add request))]
    (-> (.batchAnnotateImages client requests)
        (.getResponsesList)
        parse-crop-hints)))

(defn normalized-verts-as-rect
  "convert normalized vertices to rectangular pixel coordinates"
  [^BufferedImage image vertices]
  (let [[p1 p2 p3 p4] vertices
        image-width (.getWidth image)
        image-height (.getHeight image)]
    {:x (* image-width (:x p1))
     :y (* image-height (:y p1))
     :w (* image-width (- (:x p2) (:x p1)))
     :h (* image-height (- (:y p3) (:y p2)))}))

(defn show-as-subimage
  "use vertices to display subimage of localized object"
  [image vertices label]
  (let [{:keys [x y w h]} (normalized-verts-as-rect image vertices)]
    (-> image (img/sub-image x y w h) (img/show label))))

(defn- save
  "Save an annotated image to the results directory"
  [img outfile]
  (ImageIO/write img "jpg" (io/file (str "./results/" outfile))))

(defn annotate-image-with-obj-localization
  "Submit the image annotation request"
  [filename & {:keys [show] :or {show false}}]
  (let [image (img/as-image filename)
        request (build-request filename :obj-localization)
        requests (doto (new ArrayList) (.add request))
        responses (-> (.batchAnnotateImages client requests)
                      (.getResponsesList))]
    (for [resp (->> responses .iterator iterator-seq)]
      (let [entities  (.getLocalizedObjectAnnotationsList resp)
            mapper (fn [ann] (let [vertz (:vertices ann)
                                    {:keys [x y w h]} (normalized-verts-as-rect image vertz)]
                               {:img (img/annotate-image! image x y w h (:name ann))
                                :label (:name ann)
                                :x (int x) :y (int y)
                                :w (int w) :h (int h)}))
            annotations (for [entity entities]
                          (let [verts (for [v (-> entity .getBoundingPoly .getNormalizedVerticesList)]
                                        {:x (.getX v) :y (.getY v)})
                                {:keys [x y w h]} (normalized-verts-as-rect image verts)
                                name (.getName entity)]
                            ;;            (show-as-subimage image verts name)
                            ;;            (img/annotate-image! image x y w h name)
                            {:name name
                             :score (.getScore entity)
                             :vertices verts}))]
        
        ;;(img/show image)
        (let [results (map mapper annotations)
              img (-> results last :img)]
          (if img
            (do
              (when show (img/show img filename))
              (save img filename)
              {:file filename
               :objs (mapv #(dissoc % :img) results)})
            {:file filename
             :objs []}))))))

(comment
  ;; annotate an image
  (def response (annotate-image-as-maps file-name))
  (def response (annotate-image-with-crop-hints file-name))
  (def response (annotate-image-with-obj-localization file-name))

  (ImageIO/write (first img) "jpg" (io/file "./resources/annotated.jpg"))

  ;; enable the knowledge graph api before using
  ;;https://kgsearch.googleapis.com/v1/entities:search?ids=kg:/m/01q0j8&key=[API-KEY]

  ;; example "mixture"
  ;;https://kgsearch.googleapis.com/v1/entities:search?ids=/m/01q0j8&key=[API-KEY]

  )



