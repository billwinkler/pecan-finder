(ns pecan-finder.image
  (:require [clojure.java.io :as io])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio ImageIO]
           [pecan_finder Frame]))


(def file-name "20200901_120215.JPG") ;; pecans in grass
(def file-name "20200901_120244.JPG") ;; pecans on pavement
(def file-name "20200901_120925.JPG") ;; "animal"

(defn show
  "Display an image in a `JFrame`.
  Convenience function for viewing an image quickly."
  [^BufferedImage image & title]
  (Frame/createImageFrame (or (first title) "Quickview") image))

(defn ^BufferedImage as-image
  "Load a resource as a java BufferedImage"
  [filename]
  (-> (str "images/" filename) io/resource ImageIO/read))

(defn image-size
  "Compute image height and width"
  [filename]
  (let [image (-> (str "images/" filename ) io/resource io/file (ImageIO/read))]
    {:width (.getWidth image)
     :height (.getHeight image)}))

;; (-> "bicycle.jpg" as-image show)

(defn sub-image
  "Gets a sub-image area from an image."
  (^BufferedImage [^BufferedImage image x y w h]
   (.getSubimage image (int x) (int y) (int w) (int h))))

;; (-> "20200901_120925.JPG" as-image (sub-image 2451 1268 365 411) show)

(defn annotate-image!
  "Draw a labeled rectangle on an image"
  [^BufferedImage img x y w h label]
  (let [g (.createGraphics img)
        p 10]
    (doto g
      (.setColor Color/RED)
      (.drawRect x y w h)
      (.drawString label (int x) (int y))
      (.fillOval (+ x (/ w 2) (/ (- p) 2)) (+ y (/ h 2) (/ (- p) 2)) p p)
      (.dispose)))
  img)




