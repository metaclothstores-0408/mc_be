package com.metacloth.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metacloth.api.dto.DtfDtos.DtfDownloadRequest;
import com.metacloth.api.dto.DtfDtos.DtfPrintParams;
import com.metacloth.config.AppProperties;
import com.metacloth.domain.entity.DtfProductionAsset;
import com.metacloth.domain.entity.OrderItem;
import com.metacloth.repository.DtfProductionAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
/**
 * Independent DTF production pipeline — isolated middleware.
 * <p>
 * <b>Responsibilities (decoupled from core Order/Studio flow):</b>
 * - Async order processing: parses {@code dtf_print_parameters} from {@code printConfigJson},
 *   fetches the raw high-res asset, applies chroma-key + Bayer halftone, scales to
 *   exactly {@code print_width_inches @ 300 DPI}, writes transparent PNG to disk.
 * - Synchronous on-demand download: same processing but streams bytes back
 *   with {@code Content-Disposition: attachment} for the “Download Print-Ready PNG” button.
 * <p>
 * <b>Sheet mapping — garment-invariant:</b>
 * Physical width {@code print_width_inches} is authoritative. Frontend maps
 * {@code printScale (0.4..4.0) → occupancy = scale/2 → width = sheet.width × occupancy}
 * so A3@50% = 5.85″ (spec example) regardless of S–XXL. Backend honors
 * {@code print_width_inches} directly; falls back to {@code print_scale} scaling if absent.
 * A4=8.3×11.7″ (2490×3510 @300 DPI), A3=11.7×16.5″ (3510×4950 @300 DPI).
 */
@Slf4j @Service @RequiredArgsConstructor
public class DtfProductionService {
    private final DtfProductionAssetRepository assets;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private static final int[] BAYER={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
    private static final int DPI = 300;
    private static final int MAX_PX = 9000; // safety cap for 400% bleed (e.g. A3 23.4″ → 7020 px)
    private static final double SHEET_A4_W = 8.3, SHEET_A4_H = 11.7;
    private static final double SHEET_A3_W = 11.7, SHEET_A3_H = 16.5;
    @Async
    public void processOrderAsync(Long orderId, List<OrderItem> items){
        for(OrderItem item: items){
            try{processSingleItem(item);}
            catch(Exception e){log.error("DTF failed item {}: {}",item.getId(),e.getMessage(),e);}
        }
        log.info("DTF pipeline finished order {}",orderId);
    }
    private void processSingleItem(OrderItem item) throws Exception{
        if(item.getPrintConfigJson()==null||item.getPrintConfigJson().isBlank()) return;
        JsonNode root;
        try{root=objectMapper.readTree(item.getPrintConfigJson());}
        catch(Exception e){log.warn("DTF invalid JSON item {}",item.getId());return;}
        JsonNode dtf=root.get("dtf_print_parameters");
        if(dtf==null||dtf.isNull()){log.info("DTF skip item {} no params",item.getId());return;}
        // --- Support both legacy (camel) and spec (snake_case) keys for robustness ---
        String stripHex = pickText(dtf, new String[]{"strip_color_hex","stripColorHex"}, "#ffffff");
        int sens = pickInt(dtf, new String[]{"strip_sensitivity","stripSensitivity"}, 30);
        int halftone = pickInt(dtf, new String[]{"halftone_density","halftoneDensity"}, 0);
        double printScale = pickDouble(dtf, new String[]{"print_scale","printScale"}, 1.0);
        if(printScale<0.4) printScale=0.4; if(printScale>4.0) printScale=4.0;
        // Sheet mapping — authoritative physical inches (spec); fallback computed from printScale
        String sheetPreset = pickText(dtf, new String[]{"selected_sheet_preset","selectedSheetPreset"}, "A3");
        if(!"A4".equalsIgnoreCase(sheetPreset) && !"A3".equalsIgnoreCase(sheetPreset)) sheetPreset = "A3";
        Double printWidthInches = pickDoubleOrNull(dtf, new String[]{"print_width_inches","printWidthInches"});
        if(printWidthInches == null || printWidthInches <= 0){
            // derive from printScale → occupancy model (50% at 1.0) — garment-invariant
            double sheetW = "A4".equalsIgnoreCase(sheetPreset) ? SHEET_A4_W : SHEET_A3_W;
            printWidthInches = Math.round(sheetW * (printScale/2.0) * 100.0)/100.0;
        }
        Double sheetW = pickDoubleOrNull(dtf, new String[]{"sheet_width_inches","sheetWidthInches"});
        Double sheetH = pickDoubleOrNull(dtf, new String[]{"sheet_height_inches","sheetHeightInches"});
        if(sheetW == null) sheetW = "A4".equalsIgnoreCase(sheetPreset) ? SHEET_A4_W : SHEET_A3_W;
        if(sheetH == null) sheetH = "A4".equalsIgnoreCase(sheetPreset) ? SHEET_A4_H : SHEET_A3_H;
        String originalUrl=null;
        if(dtf.has("original_asset_url")&&!dtf.get("original_asset_url").isNull()) originalUrl=dtf.get("original_asset_url").asText();
        else if(dtf.has("originalAssetUrl")&&!dtf.get("originalAssetUrl").isNull()) originalUrl=dtf.get("originalAssetUrl").asText();
        // Fallback to preview_data_url (data: URL) — needed when original was a blob: URL that Java cannot fetch
        if((originalUrl==null||originalUrl.isBlank()||originalUrl.toLowerCase().startsWith("blob:")) && dtf.has("preview_data_url") && !dtf.get("preview_data_url").isNull()){
            String cand = dtf.get("preview_data_url").asText();
            if(cand!=null && !cand.isBlank() && !cand.toLowerCase().startsWith("blob:")) originalUrl=cand;
        }
        if((originalUrl==null||originalUrl.isBlank()||originalUrl.toLowerCase().startsWith("blob:")) && dtf.has("previewDataUrl") && !dtf.get("previewDataUrl").isNull()){
            String cand2 = dtf.get("previewDataUrl").asText();
            if(cand2!=null && !cand2.isBlank() && !cand2.toLowerCase().startsWith("blob:")) originalUrl=cand2;
        }
        if((originalUrl==null||originalUrl.isBlank()||originalUrl.toLowerCase().startsWith("blob:"))&&item.getPreviewRenderUrl()!=null && !item.getPreviewRenderUrl().toLowerCase().startsWith("blob:")) originalUrl=item.getPreviewRenderUrl();
        if(originalUrl==null||originalUrl.isBlank()){log.warn("DTF no source {}",item.getId());return;}
        DtfProductionAsset asset=DtfProductionAsset.builder()
                .orderItem(item).originalAssetUrl(originalUrl)
                .stripColorHex(stripHex).stripSensitivity(sens).halftoneDensity(halftone).printScale(printScale)
                .selectedSheetPreset(sheetPreset.toUpperCase())
                .printWidthInches(printWidthInches).sheetWidthInches(sheetW).sheetHeightInches(sheetH)
                .status("PROCESSING").build();
        assets.save(asset);
        try{
            BufferedImage src=fetchImage(originalUrl);
            if(src==null) throw new IOException("decode failed "+originalUrl);
            // Physical width is authoritative: targetPx = printWidthInches × 300 DPI, preserves aspect.
            // Falls back to printScale-scaled pixel dimensions if width not provided.
            BufferedImage processed;
            if(printWidthInches != null && printWidthInches > 0){
                processed = processImageAtWidth(src, stripHex, sens, halftone, printWidthInches);
            } else {
                processed = processImage(src,stripHex,sens,halftone,printScale);
            }
            String dirStr=(props.dtf()!=null&&props.dtf().outputDir()!=null)?props.dtf().outputDir():"./storage/dtf";
            Path dir=Path.of(dirStr); Files.createDirectories(dir);
            String name=String.format("dtf_%d_%d.png",item.getId(),System.currentTimeMillis());
            Path out=dir.resolve(name);
            ImageIO.write(processed,"png",out.toFile());
            asset.setProcessedFilePath(out.toAbsolutePath().toString());
            asset.setStatus("READY"); assets.save(asset);
            log.info("DTF READY item {} -> {}",item.getId(),out);
        }catch(Exception e){
            asset.setStatus("FAILED");
            asset.setErrorMessage(e.getMessage()!=null?e.getMessage().substring(0,Math.min(900,e.getMessage().length())):"unknown");
            assets.save(asset); throw e;
        }
    }
    /** Synchronous on-demand render for the “Download Print-Ready PNG” button — isolated. */
    public byte[] renderForDownload(DtfDownloadRequest req) throws Exception {
        DtfPrintParams p = req != null ? req.dtf_print_parameters() : null;
        String hex = p!=null && p.strip_color_hex()!=null ? p.strip_color_hex() : "#ffffff";
        int sens = p!=null && p.strip_sensitivity()!=null ? p.strip_sensitivity() : 30;
        int halftone = p!=null && p.halftone_density()!=null ? p.halftone_density() : 0;
        String sheetPreset = p!=null && p.selected_sheet_preset()!=null ? p.selected_sheet_preset() : "A3";
        if(!"A4".equalsIgnoreCase(sheetPreset) && !"A3".equalsIgnoreCase(sheetPreset)) sheetPreset="A3";
        Double printWidthInches = p!=null ? p.print_width_inches() : null;
        double printScale = p!=null && p.print_scale()!=null ? p.print_scale() : 1.0;
        if(printScale<0.4) printScale=0.4; if(printScale>4.0) printScale=4.0;
        if(printWidthInches==null || printWidthInches<=0){
            double sheetW="A4".equalsIgnoreCase(sheetPreset)?SHEET_A4_W:SHEET_A3_W;
            printWidthInches=Math.round(sheetW*(printScale/2.0)*100.0)/100.0;
        }
        String source = req!=null? req.original_asset_url(): null;
        if(source==null||source.isBlank()){
            if(p!=null){
                if(p.original_asset_url()!=null && !p.original_asset_url().isBlank()) source=p.original_asset_url();
                else if(p.preview_data_url()!=null && !p.preview_data_url().isBlank()) source=p.preview_data_url();
            }
        }
        if(source==null||source.isBlank()) throw new IllegalArgumentException("No source image URL provided");
        BufferedImage src = fetchImage(source);
        if(src==null) throw new IOException("Failed to decode source image");
        BufferedImage out = processImageAtWidth(src, hex, sens, halftone, printWidthInches);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(out.getWidth()*out.getHeight()*4);
        ImageIO.write(out,"png",bos);
        log.info("DTF download render: sheet={} {}in @{}DPI → {}x{} px", sheetPreset, printWidthInches, DPI, out.getWidth(), out.getHeight());
        return bos.toByteArray();
    }

    /** Exact 300 DPI scaling: targetWidthPx = printWidthInches × 300, aspect preserved, capped. */
    private BufferedImage processImageAtWidth(BufferedImage src, String hex, int sens, int halftoneDensity, double printWidthInches){
        int targetW = (int)Math.round(Math.max(0.5, printWidthInches) * DPI);
        if(targetW < 1) targetW = 1;
        if(targetW > MAX_PX) targetW = MAX_PX;
        int sw=src.getWidth(), sh=src.getHeight();
        double aspect = sh==0?1.0: (double)sw/sh;
        int targetH = (int)Math.round(targetW / Math.max(0.01, aspect));
        if(targetH > MAX_PX) { targetH = MAX_PX; targetW = (int)Math.round(targetH*aspect); }
        BufferedImage scaled=new BufferedImage(targetW,targetH,BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2=scaled.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src,0,0,targetW,targetH,null);
        g2.dispose();
        // Reuse chroma + halftone loop on the exactly-sized image
        BufferedImage img=new BufferedImage(targetW,targetH,BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(scaled,0,0,null);
        int[] target=hexToRgb(hex);
        double maxDist=441.67295;
        double tol=(Math.max(0,Math.min(100,sens))/100.0)*maxDist;
        double feather=maxDist*0.04;
        double blend=Math.max(0,Math.min(100,halftoneDensity))/100.0;
        for(int y=0;y<targetH;y++) for(int x=0;x<targetW;x++){
            int argb=img.getRGB(x,y);
            int a=(argb>>24)&0xff; if(a==0) continue;
            int r=(argb>>16)&0xff,g=(argb>>8)&0xff,b=argb&0xff;
            if(target!=null){
                double dist=colorDistance(r,g,b,target[0],target[1],target[2]);
                if(dist<=tol){img.setRGB(x,y,0x00000000);continue;}
                if(dist<tol+feather&&tol>0){
                    double fade=(dist-tol)/feather; if(fade<0) fade=0; if(fade>1) fade=1;
                    int na=(int)Math.round(a*fade); img.setRGB(x,y,(na<<24)|(r<<16)|(g<<8)|b);
                    if(fade<0.9) continue; a=na;
                }
            }
            if(halftoneDensity>0&&a>20){
                double gray=(0.299*r+0.587*g+0.114*b)/255.0;
                int bv=BAYER[(y%4)*4+(x%4)]; double thr=bv/16.0*(0.55+blend*0.45)+(1-blend)*0.15;
                if(gray>0.35&&gray<0.92&&gray<thr+blend*0.15){
                    double hole=blend*(gray>0.6?1.0:0.6); int na=(int)Math.round(a*(1-hole*0.85));
                    img.setRGB(x,y,(na<<24)|(r<<16)|(g<<8)|b);
                }
            }
        }
        return img;
    }

    private BufferedImage processImage(BufferedImage src,String hex,int sens,int halftoneDensity,double printScale){
        // Legacy fallback: scale output by printScale (0.4→ small chest, 4.0→ bleed) — kept for backward compat
        int sw=src.getWidth(),sh=src.getHeight();
        int w=(int)Math.round(sw*Math.max(0.4,Math.min(4.0,printScale)));
        int h=(int)Math.round(sh*Math.max(0.4,Math.min(4.0,printScale)));
        BufferedImage scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2=scaled.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src,0,0,w,h,null);
        g2.dispose();
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(scaled,0,0,null);
        int[] target=hexToRgb(hex);
        double maxDist=441.67295;
        double tol=(Math.max(0,Math.min(100,sens))/100.0)*maxDist;
        double feather=maxDist*0.04;
        double blend=Math.max(0,Math.min(100,halftoneDensity))/100.0;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++){
            int argb=img.getRGB(x,y);
            int a=(argb>>24)&0xff; if(a==0) continue;
            int r=(argb>>16)&0xff,g=(argb>>8)&0xff,b=argb&0xff;
            if(target!=null){
                double dist=colorDistance(r,g,b,target[0],target[1],target[2]);
                if(dist<=tol){img.setRGB(x,y,0x00000000);continue;}
                if(dist<tol+feather&&tol>0){
                    double fade=(dist-tol)/feather;
                    if(fade<0) fade=0; if(fade>1) fade=1;
                    int na=(int)Math.round(a*fade);
                    img.setRGB(x,y,(na<<24)|(r<<16)|(g<<8)|b);
                    if(fade<0.9) continue;
                    a=na;
                }
            }
            if(halftoneDensity>0&&a>20){
                double gray=(0.299*r+0.587*g+0.114*b)/255.0;
                int bv=BAYER[(y%4)*4+(x%4)];
                double thr=bv/16.0*(0.55+blend*0.45)+(1-blend)*0.15;
                if(gray>0.35&&gray<0.92&&gray<thr+blend*0.15){
                    double hole=blend*(gray>0.6?1.0:0.6);
                    int na=(int)Math.round(a*(1-hole*0.85));
                    img.setRGB(x,y,(na<<24)|(r<<16)|(g<<8)|b);
                }
            }
        }
        return img;
    }
    private int[] hexToRgb(String hex){
        if(hex==null) return null;
        String h=hex.trim().replaceFirst("^#","");
        if(h.length()==3) h=""+h.charAt(0)+h.charAt(0)+h.charAt(1)+h.charAt(1)+h.charAt(2)+h.charAt(2);
        if(!h.matches("^[0-9a-fA-F]{6}$")) return null;
        return new int[]{Integer.parseInt(h.substring(0,2),16),Integer.parseInt(h.substring(2,4),16),Integer.parseInt(h.substring(4,6),16)};
    }
    private double colorDistance(int r1,int g1,int b1,int r2,int g2,int b2){
        int dr=r1-r2,dg=g1-g2,db=b1-b2; return Math.sqrt(dr*dr+dg*dg+db*db);
    }
    private BufferedImage fetchImage(String url) throws Exception{
        if(url == null || url.isBlank()) throw new IllegalArgumentException("Empty source URL");
        String low = url.toLowerCase();
        if(low.startsWith("blob:")){
            throw new IllegalArgumentException("Blob URL is local to the browser and cannot be fetched by the server. Please re-select the file so it is sent as a data: URL or an http(s) URL.");
        }
        if(url.startsWith("data:")){
            int c=url.indexOf(',');
            if(c<0) throw new IOException("Invalid data URL");
            String b64 = url.substring(c+1);
            // handle data:image/png;base64,xxx and bare base64
            b64 = b64.trim();
            // Some upload previews may be very large data URLs — ignore whitespace
            byte[] bytes=Base64.getDecoder().decode(b64.replaceAll("\\s+",""));
            try(ByteArrayInputStream bis=new ByteArrayInputStream(bytes)){return ImageIO.read(bis);}
        }
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Invalid URL scheme: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid URL host");
        }
        InetAddress address = InetAddress.getByName(host);
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress() || host.equalsIgnoreCase("localhost")) {
            throw new SecurityException("Access to local/private network address is prohibited: " + host);
        }
        URL u = uri.toURL();
        HttpURLConnection conn=(HttpURLConnection)u.openConnection();
        conn.setConnectTimeout(8000); conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent","MetaCloth-DTF/1.0");
        conn.setInstanceFollowRedirects(true);
        int code=conn.getResponseCode();
        if(code<200||code>=300) throw new IOException("HTTP "+code+" "+url);
        try(InputStream in=conn.getInputStream()){return ImageIO.read(in);}
        finally{conn.disconnect();}
    }

    // ── helpers — tolerant key picking so both snake_case and legacy forms work ──
    private String pickText(JsonNode n, String[] keys, String def){
        for(String k: keys){ if(n.has(k) && !n.get(k).isNull()){ String v=n.get(k).asText(def); if(v!=null&&!v.isBlank()) return v; } }
        return def;
    }
    private int pickInt(JsonNode n, String[] keys, int def){
        for(String k: keys){ if(n.has(k) && !n.get(k).isNull() && n.get(k).canConvertToInt()) return n.get(k).asInt(def); }
        return def;
    }
    private double pickDouble(JsonNode n, String[] keys, double def){
        for(String k: keys){ if(n.has(k) && !n.get(k).isNull() && n.get(k).isNumber()) return n.get(k).asDouble(def); }
        return def;
    }
    private Double pickDoubleOrNull(JsonNode n, String[] keys){
        for(String k: keys){ if(n.has(k) && !n.get(k).isNull() && n.get(k).isNumber()) return n.get(k).asDouble(); }
        return null;
    }
}
