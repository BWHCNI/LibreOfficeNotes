/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nrims.unoplugin;

/**
 *
 * @author wang2
 */

import ij.*;
import ij.plugin.*;
import java.awt.image.BufferedImage;
import java.awt.Image;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import com.sun.star.accessibility.AccessibleRole;
import com.sun.star.awt.Point;
import com.sun.star.awt.Size;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.drawing.XShape;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XDesktop;
import com.sun.star.graphic.XGraphic;
import com.sun.star.graphic.XGraphicProvider;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.WrapTextMode;
import com.sun.star.text.TextContentAnchorType;
import com.sun.star.uno.XComponentContext;
import com.sun.star.beans.PropertyValue;
import com.sun.star.container.XNameAccess;
import com.sun.star.lib.uno.adapter.ByteArrayToXInputStreamAdapter;
import com.sun.star.container.XNamed;
import com.sun.star.text.XTextFrame;
import com.sun.star.text.XTextFramesSupplier;
import com.sun.star.accessibility.XAccessible;
import com.sun.star.accessibility.XAccessibleComponent;
import com.sun.star.accessibility.XAccessibleContext;
import com.sun.star.awt.XUnitConversion;
import com.sun.star.awt.XWindow;
import com.sun.star.awt.XWindowPeer;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexAccess;
import com.sun.star.drawing.FillStyle;
import com.sun.star.drawing.XDrawPage;
import com.sun.star.drawing.XDrawPagesSupplier;
import com.sun.star.drawing.XShapeGroup;
import com.sun.star.drawing.XShapeGrouper;
import com.sun.star.drawing.XShapes;
import com.sun.star.frame.XController;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.text.XTextEmbeddedObjectsSupplier;
import com.sun.star.text.XTextViewCursor;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.Any;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XInterface;
import com.sun.star.util.MeasureUnit;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
/**
 *
 * @author wang2
 */
public class UnoPlugin implements PlugIn{

    private XComponentContext context;
    private XMultiComponentFactory xMCF;
    private UnoPluginWindow unoPluginWindow;
    private ArrayList<ImageListenerPair> pairs = new ArrayList<ImageListenerPair>();
    public boolean dropToResize;
    public boolean fitToWindow;
    public boolean autoTile;
    private boolean setupByMims;
    private Thread t;
    private ArrayList<Integer> mimsImages = new ArrayList<Integer>();
    public static UnoPlugin unoPlugin;
    
    public UnoPlugin(){
        UnoPlugin.unoPlugin = this;
        dropToResize = true;
        fitToWindow = true;
        autoTile = true;
        String pluginPath = IJ.getDirectory("macros");
        File file = new File(pluginPath + "/dragdrop_tools.fiji.ijm");
        if (file.exists()) {
            IJ.run("Install...", "install=" + pluginPath + "/dragdrop_tools.fiji.ijm");
        } else {
            IJ.error("Error: dragdrop_tools.fiji.ijm does not exist. Please try updating.");
        }
        t = new Thread(new ListenerAdder());
        t.start();

    }
    public UnoPlugin(boolean mims){
        UnoPlugin.unoPlugin = this;
        setupByMims = mims;
        dropToResize = true;
        fitToWindow = true;
        autoTile = true;
        t = new Thread(new ListenerAdder());
        t.start();
    }
    public static UnoPlugin getInstance(){
        return (UnoPlugin)unoPlugin;
    }
    public void addMimsImage(ImagePlus image){
        mimsImages.add(image.getID());
    }
    @Override
    public void run(String arg){
        unoPluginWindow = new UnoPluginWindow("Libreoffice DragDrop", this);
        unoPluginWindow.setVisible(true);
        WindowAdapter windowAdapter = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                if (!setupByMims){
                    t.interrupt();
                    destroyAllListeners();
                    
                    String pluginPath = IJ.getDirectory("macros");
                    IJ.run("Install...", "install=" + pluginPath + "/StartupMacros.fiji.ijm");
                    unoPluginWindow = null;
                }
            }
        };
        unoPluginWindow.addWindowListener(windowAdapter);
        //unoPluginWindow.show();
                 
    }
    /**
     * Class to pair image and a listener together
     */
    public class ImageListenerPair{
        ImagePlus img;
        MouseListener ml;
        public ImageListenerPair(ImagePlus img, MouseListener ml){
            this.img = img;
            this.ml = ml;
            img.getWindow().getCanvas().addMouseListener(this.ml);
        }
        public void destroy(){
            if (img != null){
                if (img.getWindow() != null){
                    if (img.getWindow().getCanvas() != null){
                        img.getWindow().getCanvas().removeMouseListener(ml);
                    }
                }
            }
        }
    }
    /**
     * Thread to run in background attaching listeners to any new images;
     */
    public class ListenerAdder implements Runnable{
        public void run() {
            int count = ij.WindowManager.getImageCount();
            for (;;) {
                try {
                    if (ij.WindowManager.getImageCount() != count) {
                        count = ij.WindowManager.getImageCount();
                        destroyAllListeners();
                        setListeners();
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
    /**
     * Implementation of MouseListener to trigger image drop into Libreoffice
     */
    public class dragListener implements MouseListener{
        ImagePlus img;
        public dragListener(ImagePlus img){
            this.img = img;
        }
        @Override
        public void mousePressed(MouseEvent e) {
	}

        @Override
	public void mouseReleased(MouseEvent e) {
            if (IJ.getToolName().equals("Drag To Writer tool") && !mimsImages.contains(img.getID())) {
                dropImage(getScreenCaptureCurrentImage(), img.getTitle(), img.getTitle(), img.getTitle());
            }
	}
        @Override
        public void mouseExited(MouseEvent e) {}
        @Override
	public void mouseClicked(MouseEvent e) {}	
        @Override
	public void mouseEntered(MouseEvent e) {}
    }
    /**
     * Add listener to all non-OpenMims images
     */
    public void setListeners(){
        int[] ids = ij.WindowManager.getIDList();
        if (ids != null) {
            for (int i = 0; i < ids.length; i++) {
                ImagePlus img = ij.WindowManager.getImage(ids[i]);
                if (!mimsImages.contains(img.getID())) {
                    dragListener dl = new dragListener(img);
                    pairs.add(new ImageListenerPair(img, dl));
                }
            }
        }
    }
    /**
     * Delete all listener attacked to all images
     */
    public void destroyAllListeners(){
        for (int i = 0; i < pairs.size(); i++){
            pairs.get(i).destroy();
        }
        pairs = new ArrayList<ImageListenerPair>();
    }
    /**
     * Gets a screen capture for the current image.
     *
     * @return the AWT Image.
     */
    public static Image getScreenCaptureCurrentImage() {
        ImagePlus imp = ij.WindowManager.getCurrentImage();
        final ImageWindow win = imp.getWindow();
        if (win == null) {
            return null;
        }
        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }
        java.awt.Point loc = win.getLocation();
        ImageCanvas ic = win.getCanvas();
        ic.update(ic.getGraphics());

        Rectangle bounds = ic.getBounds();
        loc.x += bounds.x;
        loc.y += bounds.y;
        Rectangle r = new Rectangle(loc.x, loc.y, bounds.width, bounds.height);
        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException ex) {
            IJ.error("Unable to capture image");
            return null;
        }
        robot.delay(100);
        Image img = robot.createScreenCapture(r);

        return img;
    }
    /**
     * Method called to get current LibreOffice document. Gets currently opened
     * document, in the form of a XComponent
     *
     * @return XComponent of currently opened document, null if none is open
     */
    private XComponent getCurrentDocument() {
        try {
            context = Bootstrap.bootstrap();
            xMCF = context.getServiceManager();
            Object oDesktop = xMCF.createInstanceWithContext(
                    "com.sun.star.frame.Desktop", context);
            XDesktop desktop = (com.sun.star.frame.XDesktop) UnoRuntime.queryInterface(
                    com.sun.star.frame.XDesktop.class, oDesktop);
            //Get the document with focus here
            XComponent currentDocument = desktop.getCurrentComponent();
            return currentDocument;
        } catch (Exception e) {
            System.out.println("Failure to connect");
        }
        return null;
    }
     private boolean isImpress(XComponent xComponent){
        XModel xModel = (XModel) UnoRuntime.queryInterface(XModel.class, xComponent);
        XServiceInfo xServiceInfo = (XServiceInfo) UnoRuntime.queryInterface(XServiceInfo.class, xModel);
        if (xServiceInfo.supportsService("com.sun.star.presentation.PresentationDocument"))
            return true;
        else return false;
    }
    /**
     * 
     */
    /**
     * Open a new writer document for taking notes.
     *
     * @return true on success, false otherwise
     */
    public static boolean newDoc() {
        try {
            XComponentContext context = Bootstrap.bootstrap();
            XMultiComponentFactory xMCF = context.getServiceManager();
            Object oDesktop = xMCF.createInstanceWithContext(
                    "com.sun.star.frame.Desktop", context);
            XDesktop desktop = (com.sun.star.frame.XDesktop) UnoRuntime.queryInterface(
                    com.sun.star.frame.XDesktop.class, oDesktop);
            XComponentLoader xComponentLoader = (XComponentLoader) UnoRuntime.queryInterface(
                    XComponentLoader.class, desktop);
            PropertyValue[] loadProps = new PropertyValue[0];
            XComponent currentDocument = xComponentLoader.loadComponentFromURL("private:factory/swriter", "_blank", 0, loadProps);
            return true;
        } catch (Exception e) {
            System.out.println("Failure to create new document");
            return false;
        }
    }
    public static boolean newDraw() {
        try {
            XComponentContext context = Bootstrap.bootstrap();
            XMultiComponentFactory xMCF = context.getServiceManager();
            Object oDesktop = xMCF.createInstanceWithContext(
                    "com.sun.star.frame.Desktop", context);
            XDesktop desktop = (com.sun.star.frame.XDesktop) UnoRuntime.queryInterface(
                    com.sun.star.frame.XDesktop.class, oDesktop);
            XComponentLoader xComponentLoader = (XComponentLoader) UnoRuntime.queryInterface(
                    XComponentLoader.class, desktop);
            PropertyValue[] loadProps = new PropertyValue[0];
            XComponent currentDocument = xComponentLoader.loadComponentFromURL("private:factory/sdraw", "_blank", 0, loadProps);
            return true;
        } catch (Exception e) {
            System.out.println("Failure to create new document");
            return false;
        }
    }
    public static boolean newImpress() {
        try {
            XComponentContext context = Bootstrap.bootstrap();
            XMultiComponentFactory xMCF = context.getServiceManager();
            Object oDesktop = xMCF.createInstanceWithContext(
                    "com.sun.star.frame.Desktop", context);
            XDesktop desktop = (com.sun.star.frame.XDesktop) UnoRuntime.queryInterface(
                    com.sun.star.frame.XDesktop.class, oDesktop);
            XComponentLoader xComponentLoader = (XComponentLoader) UnoRuntime.queryInterface(
                    XComponentLoader.class, desktop);
            PropertyValue[] loadProps = new PropertyValue[0];
            XComponent currentDocument = xComponentLoader.loadComponentFromURL("private:factory/simpress", "_blank", 0, loadProps);
            return true;
        } catch (Exception e) {
            System.out.println("Failure to create new document");
            return false;
        }
    }
    /**
     * Insert a new OLE object into the writer document to place images into
     */
    public static void insertEmptyOLEObject() {
        try {
             
            XComponentContext context = Bootstrap.bootstrap();
            XMultiComponentFactory xMCF = context.getServiceManager();
            Object oDesktop = xMCF.createInstanceWithContext(
                    "com.sun.star.frame.Desktop", context);
            XDesktop desktop = (com.sun.star.frame.XDesktop) UnoRuntime.queryInterface(
                    com.sun.star.frame.XDesktop.class, oDesktop);
            XComponent currentDocument = desktop.getCurrentComponent();
            XTextDocument xTextDocument = (XTextDocument) UnoRuntime.queryInterface(
                    XTextDocument.class, currentDocument);
            //current document is not a writer
            if (xTextDocument != null) {
                XMultiServiceFactory xMSF = (XMultiServiceFactory) UnoRuntime.queryInterface(
                        XMultiServiceFactory.class, xTextDocument);
                XTextContent xt = (XTextContent) UnoRuntime.queryInterface(XTextContent.class,
                        xMSF.createInstance("com.sun.star.text.TextEmbeddedObject"));
                XPropertySet xps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xt);
                xps.setPropertyValue("CLSID", "4BAB8970-8A3B-45B3-991c-cbeeac6bd5e3");
                XModel xModel = (XModel)UnoRuntime.queryInterface(
                XModel.class, currentDocument);
                XController xController = xModel.getCurrentController();
                XTextViewCursorSupplier xViewCursorSupplier = (XTextViewCursorSupplier)UnoRuntime.queryInterface(
                XTextViewCursorSupplier.class, xController);

                XTextViewCursor xViewCursor = xViewCursorSupplier.getViewCursor();
                Point p = xViewCursor.getPosition();
                xps.setPropertyValue("HoriOrientPosition", new Integer(p.X));
                xps.setPropertyValue("VertOrientPosition", new Integer(p.Y));
                
                XTextCursor cursor = xViewCursor;
                XTextRange xTextRange = (XTextRange) UnoRuntime.queryInterface(XTextRange.class, cursor);
                xTextDocument.getText().insertTextContent(xTextRange, xt, false);
            }
        } catch (Exception ex) {
            System.out.println("Could not insert OLE object");
            ex.printStackTrace(System.err);
        }
    }

    /**
     * Method to handle dropping images in LibreOffice. If the user drops
     * outside a text frame, nothing happens. If the user drops inside a text
     * frame, and over no images, a new image is inserted into the text frame If
     * the user drops inside a text frame and over an image, the existing image
     * is replaced with the new one, albeit with same size and position
     * @param i java.awt.image to be inserted
     * @param text caption for the image
     * @param title title for the image, under "Description..."
     * @param description description for the image, under "Description..."
     * @return true if succeeded, false if not
     */
    public boolean dropImage(Image i, String text, String title, String description) {
        if (title.equals("")) {
            title = "None";
        }
        if (description.equals("")) {
            description = "None";
        }
        ImageInfo image = new ImageInfo(i, text, title, description);
        XComponent currentDocument = getCurrentDocument();
        if (currentDocument == null) {
            return false;
        }
        try {
            // Querying for the text interface
            XTextDocument xTextDocument = (XTextDocument) UnoRuntime.queryInterface(
                    XTextDocument.class, currentDocument);
            //current document is not a writer
            if (xTextDocument == null) {
                //check if an draw doc
                XDrawPage xDrawPage = getXDrawPage(currentDocument);
                if (xDrawPage != null) {
                    System.out.println("Current document is a draw");
                    insertIntoDraw(currentDocument, image);
                }
            } else {
                System.out.println("Current document is a writer");
                insertIntoWriter(image, currentDocument);
            }

        } catch (Exception e) {
            System.out.println("Error reading frames");
            e.printStackTrace(System.err);
            return false;
        }
        return true;
    }
    /**
     * Find where to insert into the writer document.
     * Also find if we need to copy an image's dimensions.
     * @param image image to insert
     * @param xComponent component of main window
     * @return true if succeeded, false if not
     */
    private boolean insertIntoWriter(ImageInfo image, XComponent xComponent) {
        try {
            XTextDocument xTextDocument = (XTextDocument) UnoRuntime.queryInterface(
                    XTextDocument.class, xComponent);
            // Querying for the text service factory
            XMultiServiceFactory xMSF = (XMultiServiceFactory) UnoRuntime.queryInterface(
                    XMultiServiceFactory.class, xTextDocument);
            XAccessible mXRoot = makeRoot(xMSF, xTextDocument);
            XAccessibleContext xAccessibleRoot = mXRoot.getAccessibleContext();

            //scope: xTextDocument -> ScrollPane -> Document
            //get the scroll pane object
            XAccessibleContext xAccessibleContext = getNextContext(xAccessibleRoot, 0);

            //get the document object
            xAccessibleContext = getNextContext(xAccessibleContext, 0);

            int numChildren = xAccessibleContext.getAccessibleChildCount();
            //loop through all the children of the document and find the text frames
            for (int i = 0; i < numChildren; i++) {
                XAccessibleContext xChildAccessibleContext = getNextContext(xAccessibleContext, i);
                if (xChildAccessibleContext.getAccessibleRole() == AccessibleRole.TEXT_FRAME && withinRange(xChildAccessibleContext)) {
                    //loop through all images in text frame to see if we are over any of them
                    XTextFrame xTextFrame = getFrame(xChildAccessibleContext.getAccessibleName(), xTextDocument);
                    if (dropToResize) {
                        numChildren = xChildAccessibleContext.getAccessibleChildCount();
                        for (int j = 0; j < numChildren; j++) {
                            xChildAccessibleContext = getNextContext(xAccessibleContext, j);
                            if (xChildAccessibleContext.getAccessibleRole() == AccessibleRole.GRAPHIC && withinRange(xChildAccessibleContext)) {
                                //if we are over the image, then we insert a new image scaled to the width of the one we're dropping on
                                XUnitConversion xUnitConversion = getXUnitConversion(xComponent);
                                
                                XAccessibleComponent xAccessibleComponent = UnoRuntime.queryInterface(
                                        XAccessibleComponent.class, xChildAccessibleContext);
                                Size size = xUnitConversion.convertSizeToLogic(xAccessibleComponent.getSize(), MeasureUnit.MM_100TH);
                                image.size.Width = size.Width;
                                j = numChildren;
                            }
                        }
                    }
                    return insertTextContent(image, xTextFrame, xComponent, xChildAccessibleContext);
                } else if (xChildAccessibleContext.getAccessibleRole() == AccessibleRole.EMBEDDED_OBJECT && withinRange(xChildAccessibleContext)) {
                    //user is over an OLE embedded object
                    XComponent xcomponent = getOLE(xChildAccessibleContext.getAccessibleName(), xTextDocument);
                    return insertDrawContent(image, xcomponent, xChildAccessibleContext, xComponent);
                }
            }
            //if we hit here, this means we did not hit a text frame or OLE object
            if (withinRange(xAccessibleContext)) {
                XUnitConversion xUnitConversion = getXUnitConversion(xComponent);
                xAccessibleContext = getNextContext(xAccessibleContext, 0);
                XAccessibleComponent xAccessibleComponent = UnoRuntime.queryInterface(
                        XAccessibleComponent.class, xAccessibleContext);
                Point point = xAccessibleComponent.getLocationOnScreen();
                java.awt.Point location = MouseInfo.getPointerInfo().getLocation();
                image.p = xUnitConversion.convertPointToLogic(
                        new Point((int) Math.round(location.getX() - point.X), (int) Math.round(location.getY() - point.Y)),
                        MeasureUnit.MM_100TH);
                insertTextContent(image, null, xComponent, xAccessibleContext);
            }
        } catch (Exception e) {
            System.out.println("Error with accessibility api");
            e.printStackTrace(System.err);
            return false;
        }
        return true;
    }
    /**
     * Find where to insert into the draw document.
     * Also find if we need to copy an image's dimensions.
     * @param xComponent component of draw window
     * @param image image to insert 
     * @return true if succeeded, false if not
     */
    private boolean insertIntoDraw(XComponent xComponent, ImageInfo image) {
        try {
            XModel xModel = (XModel) UnoRuntime.queryInterface(XModel.class, xComponent);
            XMultiServiceFactory xMSF = (XMultiServiceFactory) UnoRuntime.queryInterface(
                    XMultiServiceFactory.class, xModel);
            XAccessible mXRoot = makeRoot(xMSF, xModel);
            XAccessibleContext xAccessibleRoot = mXRoot.getAccessibleContext();
            //go into AccessibleRole 40 (panel)
            XAccessibleContext xAccessibleContext = getNextContext(xAccessibleRoot, 0);

            //go into AccessibleRole 51 (scroll pane)
            xAccessibleContext = getNextContext(xAccessibleContext, 0);

            //go into AccessibleRole 13 (document)
            xAccessibleContext = getNextContext(xAccessibleContext, 0);

            //check to see whether if in range of document
            if (withinRange(xAccessibleContext)) {
                int numChildren = xAccessibleContext.getAccessibleChildCount();
                //loop through all the children of the document
                if (dropToResize) {
                    for (int i = 0; i < numChildren; i++) {
                        XAccessibleContext xChildAccessibleContext = getNextContext(xAccessibleContext, i);
                        //if we are over an image and it has a description (so from OpenMIMS), adjust our height
                        if (xChildAccessibleContext.getAccessibleRole() == AccessibleRole.LIST_ITEM
                                && !xChildAccessibleContext.getAccessibleDescription().isEmpty()
                                && withinRange(xChildAccessibleContext)) {
                            XAccessibleComponent xAccessibleComponent = UnoRuntime.queryInterface(
                                    XAccessibleComponent.class, xChildAccessibleContext);
                            XUnitConversion xUnitConversion = getXUnitConversion(xComponent);
                            Size size = xUnitConversion.convertSizeToLogic(xAccessibleComponent.getSize(), MeasureUnit.MM_100TH);
                                image.size.Width = size.Width;
                            break;
                        }
                    }
                }
                XUnitConversion xUnitConversion = getXUnitConversion(xComponent);
                xAccessibleContext = getNextContext(xAccessibleContext, 0);
                XAccessibleComponent xAccessibleComponent = UnoRuntime.queryInterface(
                        XAccessibleComponent.class, xAccessibleContext);
                Point point = xAccessibleComponent.getLocationOnScreen();
                java.awt.Point location = MouseInfo.getPointerInfo().getLocation();
                image.p = xUnitConversion.convertPointToLogic(
                        new Point((int) Math.round(location.getX() - point.X), (int) Math.round(location.getY() - point.Y)),
                        MeasureUnit.MM_100TH);
                insertDrawContent(image, xComponent, xAccessibleRoot, null);
            }
        } catch (Exception e) {
            System.out.println("Error with accessibility api");
            e.printStackTrace(System.err);
            return false;
        }
        return true;

    }
    /**
     * Convert and insert image and relevant info into Writer doc
     * @param image image and info to insert
     * @param xTextFrame textframe to insert into, null if none
     * @param xComponent component of writer window
     * @param xAccessibleContext accessible context of what we are inserting into
     * @return true if succeeded, false if not
     */
    private boolean insertTextContent(ImageInfo image, XTextFrame xTextFrame, XComponent xComponent, XAccessibleContext xAccessibleContext) {
        XTextDocument xTextDocument;
        try {
            //create blank graphic in document
            xTextDocument = (XTextDocument) UnoRuntime.queryInterface(
                    XTextDocument.class, xComponent);
            Object graphic = createBlankGraphic(xTextDocument);

            //query for the interface XTextContent on the GraphicObject 
            image.xImage = (com.sun.star.text.XTextContent) UnoRuntime.queryInterface(
                    com.sun.star.text.XTextContent.class, graphic);

            //query for the properties of the graphic
            XUnitConversion xUnitConversion = getXUnitConversion(xComponent);
            Size size = new Size(image.image.getWidth(null), image.image.getHeight(null));
            size = xUnitConversion.convertSizeToLogic(size, MeasureUnit.MM_100TH);
            if (image.size.Width > 0) {
                //calculate the width and height
                double ratio = (double) image.size.Width / (double) size.Width;
                image.size.Height = (int) Math.round(ratio * size.Height);
            }else{
                image.size = size;
            }
            
            XAccessibleComponent xAccessibleComponent = UnoRuntime.queryInterface(
                    XAccessibleComponent.class, xAccessibleContext);
            Size windowSize = xUnitConversion.convertSizeToLogic(xAccessibleComponent.getSize(), MeasureUnit.MM_100TH);
            //if the image is greater than the width, then we scale it down the barely fit in the page
            if (fitToWindow) {
                if (image.size.Width > windowSize.Width) {
                    int ratio = image.size.Width;
                    image.size.Width = windowSize.Width - 1000;
                    ratio = image.size.Width / ratio;
                    image.size.Height = image.size.Height * ratio;
                }
                //if greater than height, do the same thing to descale it
                /*if (image.size.Height >= windowSize.Height) {
                    double ratio = image.size.Height;
                    image.size.Height = windowSize.Height - 2500;
                    ratio = image.size.Height / ratio;
                    image.size.Width = (int) Math.round(image.size.Width * ratio);
                }*/
            }
            //set the TextContent properties
            com.sun.star.beans.XPropertySet xPropSet = (com.sun.star.beans.XPropertySet) UnoRuntime.queryInterface(
                    com.sun.star.beans.XPropertySet.class, graphic);
            xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AT_FRAME);
            xPropSet.setPropertyValue("Width", image.size.Width);
            xPropSet.setPropertyValue("Height", image.size.Height);
            xPropSet.setPropertyValue("Graphic", convertImage(image.image));
            xPropSet.setPropertyValue("TextWrap", WrapTextMode.NONE);
            xPropSet.setPropertyValue("Title", image.title);
            xPropSet.setPropertyValue("Description", image.description);
        } catch (Exception exception) {
            System.out.println("Couldn't set image properties");
            exception.printStackTrace(System.err);
            return false;
        }
        //insert the content
        return insertImageIntoTextFrame(image, xTextFrame, xTextDocument);

    }
    /**
     * Inserts content and info into a draw page.
     * @param image image and info to insert
     * @param xComponent component of draw page
     * @param xAccessibleContext accessiblecontext of draw page
     * @param parentComponent parent component of draw page, if one exists
     * @return true if succeeded, false if not
     */
    private boolean insertDrawContent(ImageInfo image, XComponent xComponent, XAccessibleContext xAccessibleContext, XComponent parentComponent) {
        Size size;
        Point point;
        XDrawPage xDrawPage = getXDrawPage(xComponent);
        XUnitConversion xUnitConversion;
        if (xDrawPage == null) {
            return false;
        }
        try {
            if (parentComponent == null) {
                xUnitConversion = getXUnitConversion(xComponent);
            } else {
                xUnitConversion = getXUnitConversion(parentComponent);
            }
            //create blank graphic in document
            Object graphic = createBlankGraphic(xComponent);

            //query for the interface XTextContent on the GraphicObject 
            image.xShape = (XShape) UnoRuntime.queryInterface(
                    XShape.class, graphic);

            //query for the properties of the graphic
            com.sun.star.beans.XPropertySet xPropSet = (com.sun.star.beans.XPropertySet) UnoRuntime.queryInterface(
                    com.sun.star.beans.XPropertySet.class, graphic);

            size = new Size(image.image.getWidth(null), image.image.getHeight(null));
            size = xUnitConversion.convertSizeToLogic(size, MeasureUnit.MM_100TH);
            if (image.size.Width > 0) {
                //calculate the width and height
                double ratio = (double) image.size.Width / (double) size.Width;
                image.size.Height = (int) Math.round(ratio * size.Height);
            }else{
                image.size = size;
            }
            XAccessibleComponent xAccessibleComponent = UnoRuntime.queryInterface(
                    XAccessibleComponent.class, xAccessibleContext);
            Size windowSize = xUnitConversion.convertSizeToLogic(xAccessibleComponent.getSize(), MeasureUnit.MM_100TH);
            //if the image is greater than the width, then we scale it down to fit in the page
            if (fitToWindow) {
                if (image.size.Width > windowSize.Width) {
                    double ratio = image.size.Width;
                    image.size.Width = windowSize.Width - 2500;
                    ratio = image.size.Width / ratio;
                    image.size.Height = (int) Math.round(image.size.Height * ratio);
                }
                //if greater than height, do the same thing to descale it
                if (image.size.Height >= windowSize.Height) {
                    double ratio = image.size.Height;
                    image.size.Height = windowSize.Height - 2500;
                    ratio = image.size.Height / ratio;
                    image.size.Width = (int) Math.round(image.size.Width * ratio);
                }
            }
           
            point = new Point();
            point.X = 0;
            point.Y = 0;
            //tile the images, and make sure they do not go beyond the limit of the window
            if (isImpress(xComponent)){
                point = image.p;
            }else if (autoTile) {
                int curX;
                while ((curX = intersects(point, image.size, xDrawPage)) != 0) {
                    if (curX + image.size.Width + 200 < windowSize.Width) {
                        point.X = curX;
                    } else {
                        point.X = 0;
                        point.Y += (300);
                    }
                }
            }
            image.xShape.setPosition(point);
            if (fitToWindow) {
                //if greater than height, do the same thing to descale it
                if (image.size.Height + point.Y >= windowSize.Height && image.size.Height - point.Y > 1000) {
                    double ratio = image.size.Height;
                    image.size.Height = windowSize.Height - point.Y - 2500;
                    ratio = image.size.Height / ratio;
                    image.size.Width = (int) Math.round(image.size.Width * ratio);
                }
            }
            //point.X -= Math.round(image.size.Width/2);
            //point.Y -= Math.round(image.size.Height/2);
             image.xShape.setSize(image.size);
            xPropSet.setPropertyValue("Graphic", convertImage(image.image));
            xPropSet.setPropertyValue("Title", image.title);
            xPropSet.setPropertyValue("Description", image.description);
        } catch (Exception exception) {
            System.out.println("Couldn't set image properties");
            exception.printStackTrace(System.err);
            return false;
        }
        try {
            XMultiServiceFactory xDrawFactory =
                    (XMultiServiceFactory) UnoRuntime.queryInterface(
                    XMultiServiceFactory.class, xComponent);
            Object drawShape = xDrawFactory.createInstance("com.sun.star.drawing.TextShape");
            XShape xDrawShape = (XShape) UnoRuntime.queryInterface(XShape.class, drawShape);
            xDrawShape.setSize(new Size(image.size.Width, 1000));
            xDrawShape.setPosition(new Point(point.X, point.Y + image.size.Height));

            //add OpenMims Image
            xDrawPage.add(image.xShape);

            //get properties of text shape and modify them
            XPropertySet xShapeProps = (XPropertySet) UnoRuntime.queryInterface(
                    XPropertySet.class, drawShape);
            xShapeProps.setPropertyValue("TextAutoGrowHeight", true);
            xShapeProps.setPropertyValue("TextContourFrame", true);
            xShapeProps.setPropertyValue("FillStyle", FillStyle.NONE);
            xShapeProps.setPropertyValue("LineTransparence", 100);

            //add text shape
            xDrawPage.add(xDrawShape);

            //add text into text shape and set text size
            XText xShapeText = (XText) UnoRuntime.queryInterface(XText.class, drawShape);
            XTextCursor xTextCursor = xShapeText.createTextCursor();
            XTextRange xTextRange = xTextCursor.getStart();
            XPropertySet xTextProps = (XPropertySet) UnoRuntime.queryInterface(
                    XPropertySet.class, xTextRange);
            xTextProps.setPropertyValue("CharHeight", new Float(11));
            xTextRange.setString(image.text);

            //get XShapes interface to group images
            XMultiServiceFactory xMultiServiceFactory = (XMultiServiceFactory) UnoRuntime.queryInterface(XMultiServiceFactory.class, xMCF);
            Object xObj = xMultiServiceFactory.createInstance("com.sun.star.drawing.ShapeCollection");
            XShapes xToGroup = (XShapes) UnoRuntime.queryInterface(XShapes.class, xObj);

            //add images to XShapes
            xToGroup.add(image.xShape);
            xToGroup.add(xDrawShape);

            //Group the shapes by using the XShapeGrouper
            XShapeGrouper xShapeGrouper = (XShapeGrouper) UnoRuntime.queryInterface(
                    XShapeGrouper.class, xDrawPage);
            XShapeGroup xShapeGroup = (XShapeGroup) xShapeGrouper.group(xToGroup);

            //set title and description of grouped image
            com.sun.star.beans.XPropertySet xPropSet = (com.sun.star.beans.XPropertySet) UnoRuntime.queryInterface(
                    com.sun.star.beans.XPropertySet.class, xShapeGroup);
            xPropSet.setPropertyValue("Title", image.title);
            xPropSet.setPropertyValue("Description", image.description);
        } catch (Exception e) {
            System.out.println("Couldn't insert image");
            e.printStackTrace(System.err);
            return false;
        }
        return true;
    }

    /**
     * Method to insert a textframe and image together into a text document's textframe.
     * 
     * @param image image and info to insert
     * @param destination textframe to insert into
     * @param xTextDocument document to insert into
     * @return 
     */
    private boolean insertImageIntoTextFrame(ImageInfo image, XTextFrame destination, XTextDocument xTextDocument) {
        XTextFrame xTextFrame;
        XText xText;
        XTextCursor xTextCursor;
        XTextRange xTextRange;
        try {
            XMultiServiceFactory xMSF = (XMultiServiceFactory) UnoRuntime.queryInterface(
                    XMultiServiceFactory.class, xTextDocument);
            //create a new text frame
            Object frame = xMSF.createInstance("com.sun.star.text.TextFrame");
            xTextFrame = (com.sun.star.text.XTextFrame) UnoRuntime.queryInterface(
                    com.sun.star.text.XTextFrame.class, frame);

            //set the dimensions of the new text frame
            XShape xTextFrameShape = (com.sun.star.drawing.XShape) UnoRuntime.queryInterface(
                    com.sun.star.drawing.XShape.class, frame);
            com.sun.star.awt.Size aSize = new com.sun.star.awt.Size();
            aSize.Height = image.size.Height;
            aSize.Width = image.size.Width;     
            xTextFrameShape.setSize(aSize);

            //Set the properties of the textframe
            int[] blank = new int[]{0, 0, 0, 0};
            com.sun.star.beans.XPropertySet xTFPS = (com.sun.star.beans.XPropertySet) UnoRuntime.queryInterface(
                    com.sun.star.beans.XPropertySet.class, xTextFrame);
            //remove the borders
            xTFPS.setPropertyValue("FrameIsAutomaticHeight", true);
            xTFPS.setPropertyValue("LeftBorder", blank);
            xTFPS.setPropertyValue("RightBorder", blank);
            xTFPS.setPropertyValue("TopBorder", blank);
            xTFPS.setPropertyValue("BottomBorder", blank);
            if (destination != null) {
                xTFPS.setPropertyValue("AnchorType",
                    com.sun.star.text.TextContentAnchorType.AT_FRAME);
                //insert the textframe
                xText = destination.getText();
                xTextCursor = xText.createTextCursor();
                xTextRange = xTextCursor.getStart();
                xText.insertTextContent(xTextRange, xTextFrame, true);
            } else {
                int x = image.p.X - (image.size.Width / 2);
                int y = image.p.Y - (image.size.Height / 2);
                xTFPS.setPropertyValue("AnchorType",
                    com.sun.star.text.TextContentAnchorType.AT_PARAGRAPH);
                xTFPS.setPropertyValue("VertOrient", com.sun.star.text.VertOrientation.NONE);
                xTFPS.setPropertyValue("HoriOrient", com.sun.star.text.HoriOrientation.NONE);
                xTFPS.setPropertyValue("HoriOrientRelation", com.sun.star.text.RelOrientation.PAGE_FRAME);
                xTFPS.setPropertyValue("VertOrientRelation", com.sun.star.text.RelOrientation.PAGE_FRAME);
                xTFPS.setPropertyValue("HoriOrientPosition", x);
                xTFPS.setPropertyValue("VertOrientPosition", y);
                xText = xTextDocument.getText();
                xTextCursor = xText.createTextCursor();
                xTextRange = xTextCursor.getStart();
                xText.insertTextContent(xTextRange, xTextFrame, true);
            }

            //insert the image into the textframe
            xText = xTextFrame.getText();
            xTextCursor = xText.createTextCursor();
            xTextRange = xTextCursor.getStart();
            xText.insertTextContent(xTextRange, image.xImage, true);

            //insert the caption
            xTextRange.setString(image.text);
        } catch (Exception exception) {
            System.out.println("Couldn't insert image");
            exception.printStackTrace(System.err);
            return false;
        }
        return true;
    }

    /**
     * Find a named text frame within current Writer doc
     *
     * @param name the name of the text frame
     * @return XTextFrame interface
     */
    private XTextFrame getFrame(String name, XTextDocument xTextDocument) {
        XTextFrame xTextFrame = null;
        try {
            //get the text frame supplier from the document
            XTextFramesSupplier xTextFrameSupplier =
                    (XTextFramesSupplier) UnoRuntime.queryInterface(
                    XTextFramesSupplier.class, xTextDocument);

            //get text frame objects
            XNameAccess xNameAccess = xTextFrameSupplier.getTextFrames();

            //query for the object with the desired name
            Object frame = xNameAccess.getByName(name);

            //get the XTextFrame interface
            xTextFrame = (XTextFrame) UnoRuntime.queryInterface(
                    com.sun.star.text.XTextFrame.class, frame);
        } catch (Exception e) {
            System.out.println("Could not find frame with name " + name);
            e.printStackTrace(System.err);
        }
        return xTextFrame;

    }
    /**
     * Retrieve an OLE Object from a text document.
     * @param name Name of the OLE Object
     * @param xTextDocument the text document we're looking in
     * @return the XComponent which represents the OLE
     */
    private XComponent getOLE(String name, XTextDocument xTextDocument) {
        XComponent xComponent = null;
        try {
            //get the text frame supplier from the document
            XTextEmbeddedObjectsSupplier xTextEmbeddedObjectsSupplier =
                    (XTextEmbeddedObjectsSupplier) UnoRuntime.queryInterface(
                    XTextEmbeddedObjectsSupplier.class, xTextDocument);

            //get text frame objects
            XNameAccess xNameAccess = xTextEmbeddedObjectsSupplier.getEmbeddedObjects();

            //query for the object with the desired name
            Object xTextEmbeddedObject = xNameAccess.getByName(name);
            XTextContent xTextContent = (XTextContent) UnoRuntime.queryInterface(XTextContent.class, xTextEmbeddedObject);
            //get the XTextFrame interface
            com.sun.star.document.XEmbeddedObjectSupplier xEOS = (com.sun.star.document.XEmbeddedObjectSupplier) UnoRuntime.queryInterface(com.sun.star.document.XEmbeddedObjectSupplier.class, xTextContent);
            com.sun.star.lang.XComponent xModel = xEOS.getEmbeddedObject();
            return xModel;
        } catch (Exception e) {
            System.out.println("Could not find frame with name " + name);
            e.printStackTrace(System.err);
        }
        return xComponent;

    }

    /**
     * Convert an image into a XGraphic
     *
     * @param image the java.awt.image to convert
     * @return an XGraphic which can be placed into a XTextContent
     */
    private XGraphic convertImage(Image image) {
        XGraphic xGraphic = null;
        try {
            ByteArrayToXInputStreamAdapter xSource = new ByteArrayToXInputStreamAdapter(imageToByteArray(image));
            PropertyValue[] sourceProps = new PropertyValue[2];

            //specify the byte array source
            sourceProps[0] = new PropertyValue();
            sourceProps[0].Name = "InputStream";
            sourceProps[0].Value = xSource;

            //specify the image type
            sourceProps[1] = new PropertyValue();
            sourceProps[1].Name = "MimeType";
            sourceProps[1].Value = "image/png";

            //get the graphic object
            XGraphicProvider xGraphicProvider = (XGraphicProvider) UnoRuntime.queryInterface(
                    XGraphicProvider.class,
                    xMCF.createInstanceWithContext("com.sun.star.graphic.GraphicProvider", context));
            xGraphic = xGraphicProvider.queryGraphic(sourceProps);
        } catch (Exception e) {
            System.out.println("Failed to convert image into LibreOffice graphic");
            e.printStackTrace(System.err);
        }
        return xGraphic;
    }

    /**
     * Create a blank graphic for insertion
     *
     * @return Object representing a blank Graphic
     */
    private Object createBlankGraphic(XTextDocument xTextDocument) {
        Object graphic = null;
        try {
            //create unique name based on timestamp
            long unixTime = System.currentTimeMillis() / 1000L;
            XMultiServiceFactory docServiceFactory =
                    (XMultiServiceFactory) UnoRuntime.queryInterface(
                    XMultiServiceFactory.class, xTextDocument);
            graphic = docServiceFactory.createInstance("com.sun.star.text.TextGraphicObject");
            XNamed name = (XNamed) UnoRuntime.queryInterface(XNamed.class, graphic);
            name.setName("" + unixTime);
        } catch (Exception exception) {
            System.out.println("Could not create image");
            exception.printStackTrace(System.err);
        }
        return graphic;
    }

    /**
     * Create a graphic object on specified page
     *
     * @param xDrawPage
     * @return
     */
    private Object createBlankGraphic(XComponent xDrawPage) {
        Object graphic = null;
        try {
            //create unique name based on timestamp
            long unixTime = System.currentTimeMillis() / 1000L;
            XMultiServiceFactory docServiceFactory =
                    (XMultiServiceFactory) UnoRuntime.queryInterface(
                    XMultiServiceFactory.class, xDrawPage);
            graphic = docServiceFactory.createInstance("com.sun.star.drawing.GraphicObjectShape");
            XNamed name = (XNamed) UnoRuntime.queryInterface(XNamed.class, graphic);
            name.setName("" + unixTime);
        } catch (Exception exception) {
            System.out.println("Could not create image");
            exception.printStackTrace(System.err);
        }
        return graphic;
    }
    /**
     * Get current window from given xModel
     * @param msf
     * @param xModel
     * @return XWindow object
     */
    private static XWindow getCurrentWindow(XMultiServiceFactory msf,
            XModel xModel) {
        return getWindow(msf, xModel, false);
    }

    /**
     * Check if the mouse pointer is within range of particular component
     *
     * @param xAccessibleContext the context of particular component
     * @return true if within, false if not
     */
    private boolean withinRange(XAccessibleContext xAccessibleContext) {
        //get the accessible component
        XAccessibleComponent xAccessibleComponent = UnoRuntime.queryInterface(
                XAccessibleComponent.class, xAccessibleContext);

        //get the bounds and check whether cursor is within it
        Point point = xAccessibleComponent.getLocationOnScreen();
        Size size = xAccessibleComponent.getSize();
        java.awt.Point location = MouseInfo.getPointerInfo().getLocation();
        if (point.X + size.Width < location.getX() || location.getX() < point.X || point.Y + size.Height < location.getY() || point.Y > location.getY()) {
            return false;
        } else {
            return true;
        }
    }
    /**
     * Check to see if a rectangle intersects with any objects in the XDrawPage.
     * @param p the upper-left corner of the rectangle
     * @param s the dimensions of the rectangle
     * @param xDrawPage the XDrawPage containing the objects we want to check against
     * @return 
     */
    private int intersects(Point p, Size s, XDrawPage xDrawPage) {
        Rectangle rectangle = new Rectangle(p.X, p.Y, s.Width, s.Height);
        XShapes xShapes = (XShapes) UnoRuntime.queryInterface(XShapes.class, xDrawPage);
        for (int i = 0; i < xShapes.getCount(); i++) {
            try {
                XShape xShape = (XShape) UnoRuntime.queryInterface(XShape.class, xShapes.getByIndex(i));

                //get the bounds and check whether cursor is within it
                Point point = xShape.getPosition();
                Size size = xShape.getSize();
                Rectangle targetRectangle = new Rectangle(point.X, point.Y, size.Width, size.Height);
                if (rectangle.intersects(targetRectangle)) {
                    return point.X+size.Width + 200;
                }
            } catch (Exception e) {
                System.out.println("Exception caught");
                return -1;
            }

        }
        return 0;
    }

    private static XWindow getWindow(XMultiServiceFactory msf, XModel xModel, boolean containerWindow) {
        XWindow xWindow = null;
        try {
            if (xModel == null) {
                System.out.println("invalid model (==null)");
            }
            XController xController = xModel.getCurrentController();
            if (xController == null) {
                System.out.println("can't get controller from model");
            }
            XFrame xFrame = xController.getFrame();
            if (xFrame == null) {
                System.out.println("can't get frame from controller");
            }
            if (containerWindow) {
                xWindow = xFrame.getContainerWindow();
            } else {
                xWindow = xFrame.getComponentWindow();
            }
            if (xWindow == null) {
                System.out.println("can't get window from frame");
            }
        } catch (Exception e) {
            System.out.println("caught exception while getting current window" + e);
        }
        return xWindow;
    }
    private static XAccessible getAccessibleObject(XInterface xObject) {
        XAccessible xAccessible = null;
        try {
            xAccessible = (XAccessible) UnoRuntime.queryInterface(
                    XAccessible.class, xObject);
        } catch (Exception e) {
            System.out.println("Caught exception while getting accessible object" + e);
            e.printStackTrace();
        }
        return xAccessible;
    }
    /**
     * Get the current drawpage of the given xComponent.
     * Can be used either on a draw document, impress document, or OLE object
     * @param xComponent
     * @return the XDrawPage that is currently displayed
     */
    private XDrawPage getXDrawPage(XComponent xComponent) {
        XDrawPage xDrawPage = null;
        try {
            XModel xModel = (XModel) UnoRuntime.queryInterface(XModel.class, xComponent);
            
                XController dddV = xModel.getCurrentController();
            if (dddV != null) {
                //this will work for draw and impress documents
                com.sun.star.beans.XPropertySet xTFPS = (com.sun.star.beans.XPropertySet) UnoRuntime.queryInterface(
                        com.sun.star.beans.XPropertySet.class, dddV);
                Any any = (Any) xTFPS.getPropertyValue("CurrentPage");
                xDrawPage = (XDrawPage) any.getObject();
            } else {
                //xModel.getCurrentController will fail if the XComponent belongs to an OLE object
                //so we need to treat it as a single page draw document
                XDrawPagesSupplier xDrawPagesSupplier = (XDrawPagesSupplier) UnoRuntime.queryInterface(
                        XDrawPagesSupplier.class, xComponent);
                if (xDrawPagesSupplier != null) {
                    Object drawPages = xDrawPagesSupplier.getDrawPages();
                    XIndexAccess xIndexedDrawPages = (XIndexAccess) UnoRuntime.queryInterface(
                            XIndexAccess.class, drawPages);
                    //get current draw page
                    Object drawPage = xIndexedDrawPages.getByIndex(0);
                    xDrawPage = (XDrawPage) UnoRuntime.queryInterface(XDrawPage.class, drawPage);
                }
            }
        } catch (Exception e) {
            System.out.println("Error trying to retrieve draw page" + e);
            e.printStackTrace(System.err);
        } finally {
            return xDrawPage;
        }
    }

    private static XAccessible makeRoot(XMultiServiceFactory msf, XModel aModel) {
        XWindow xWindow = getCurrentWindow(msf, aModel);
        return getAccessibleObject(xWindow);
    }
    /**
     * Get the next AccessibleContext from the parent given and index given
     * @param xAccessibleContext the context whose child you want to retrieve
     * @param i the index of the child you want to retrieve
     * @return XAccessibleContext child of the parent
     */
    private XAccessibleContext getNextContext(XAccessibleContext xAccessibleContext, int i) {
        try {
            XAccessible xAccessible = xAccessibleContext.getAccessibleChild(i);
            return xAccessible.getAccessibleContext();
        } catch (Exception e) {
             System.out.println("Error trying to retrieve draw page" + e);
            return null;
        }
    }

    /**
     * method to convert a java Image to a byte array representing a PNG image
     *
     * @param image desired image to convert
     * @return a byte array representing the given image
     */
    private byte[] imageToByteArray(Image image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedImage bimg = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
            bimg.createGraphics().drawImage(image, 0, 0, null);
            ImageIO.write(bimg, "png", baos);
            baos.flush();
            byte[] res = baos.toByteArray();
            baos.close();
            return res;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.out.println("Failure to convert image to byte array");
            return null;
        }
    }
    /**
     * Return the XUnitConversion object for the passed XComponent.
     * The XUnitConversion class is used to convert from the units of the document
     * to the pixels of the screen (Ex 10mm -> ??? pixels)
     * This will work regardless of screen dpi or the current zoom level of the doc
     * @param xComponent the component in whose context we wish to convert
     * @return the XUnitConversion class
     */
    private XUnitConversion getXUnitConversion(XComponent xComponent) {
        XUnitConversion xUnitConversion = null;
        try {
            XModel xModel = (XModel) UnoRuntime.queryInterface(XModel.class, xComponent);
            XMultiServiceFactory xMultiServiceFactory =
                    (XMultiServiceFactory) UnoRuntime.queryInterface(
                    XMultiServiceFactory.class, xComponent);
            XWindow xWindow = getCurrentWindow(xMultiServiceFactory, xModel);
            XWindowPeer xWindowPeer = UnoRuntime.queryInterface(XWindowPeer.class, xWindow);
            xUnitConversion = UnoRuntime.queryInterface(XUnitConversion.class, xWindowPeer);
        } catch (Exception e) {
            System.out.println("Error trying to get XUnitConversion" + e);
            e.printStackTrace(System.err);
        } finally {
            return xUnitConversion;
        }
    }
    /**
     * Helper class to help pass parameters through the various methods of the plugin
     */
    public class ImageInfo {

        public Point p;
        public Image image;
        public XTextContent xImage;
        public XShape xShape;
        public Size size;
        public String text;
        public String title;
        public String description;

        public ImageInfo(Image i) {
            this.image = i;
        }

        public ImageInfo(Image i, String n, String t, String d) {
            this.image = i;
            this.text = n;
            this.title = t;
            this.description = d;
            size = new Size(0, 0);
            p = new Point(0, 0);
        }
    }
}