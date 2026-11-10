import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

DOCS_IMG_DIR = r"C:\Users\ainol\Dev\FeederMY\feedermy-android\docs\images"
BRAND_DIR = r"C:\Users\ainol\Dev\FeederMY\feedermy-android\brand"
STORE_SCREENSHOTS = r"C:\Users\ainol\Dev\FeederMY\feedermy-android\store\screenshots"
ART_DIR = r"C:\Users\ainol\.gemini\antigravity\brain\ab579e03-f033-4e04-90f4-e26a37ad58f3"

os.makedirs(DOCS_IMG_DIR, exist_ok=True)

FONT_REG = r"C:\Windows\Fonts\segoeui.ttf"
FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_CODE = r"C:\Windows\Fonts\consola.ttf"

def get_font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except:
        return ImageFont.load_default()

# ==========================================
# 1. HERO BANNER (1280 x 420)
# ==========================================
def make_hero_banner():
    W, H = 1280, 420
    im = Image.new("RGBA", (W, H), (9, 24, 17, 255))
    draw = ImageDraw.Draw(im)

    # Gradient background
    for y in range(H):
        ratio = y / float(H)
        r = int(10 + (22 - 10) * ratio)
        g = int(26 + (62 - 26) * ratio)
        b = int(18 + (38 - 18) * ratio)
        draw.line([(0, y), (W, y)], fill=(r, g, b, 255))

    # Ambient radial green glow in center
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    cx, cy = W // 2, 170
    for rad in range(320, 0, -10):
        alpha = int(40 * (1 - rad / 320.0))
        gdraw.ellipse([cx - rad * 1.8, cy - rad, cx + rad * 1.8, cy + rad], fill=(46, 204, 113, alpha))
    glow = glow.filter(ImageFilter.GaussianBlur(30))
    im = Image.alpha_composite(im, glow)
    draw = ImageDraw.Draw(im)

    # Subtle tech dot grid
    grid_color = (46, 204, 113, 20)
    for x in range(20, W, 32):
        for y in range(20, H, 32):
            draw.ellipse([x - 1, y - 1, x + 1, y + 1], fill=grid_color)

    # Master Lockup
    lockup_path = os.path.join(BRAND_DIR, "CatchIt_Master_Lockup.png")
    if os.path.exists(lockup_path):
        lockup = Image.open(lockup_path).convert("RGBA")
        target_w = 460
        target_h = int(lockup.height * (target_w / lockup.width))
        lockup = lockup.resize((target_w, target_h), Image.Resampling.LANCZOS)
        lx = (W - target_w) // 2
        ly = 45
        im.paste(lockup, (lx, ly), lockup)
    
    # Kicker / Super-tagline
    kicker_font = get_font(FONT_BOLD, 15)
    kicker = "DIRECT PRASARANA AVL TELEMETRY · 2-STAGE ALARMS · 100% OFFLINE CATALOG"
    kw = kicker_font.getlength(kicker)
    draw.text(((W - kw) // 2, 215), kicker, font=kicker_font, fill=(46, 204, 113, 255))

    # Subtitle
    sub_font = get_font(FONT_REG, 23)
    subtitle = "Ultra-Fast, Native Real-Time Transit Tracker for Malaysia"
    sw = sub_font.getlength(subtitle)
    draw.text(((W - sw) // 2, 245), subtitle, font=sub_font, fill=(240, 246, 250, 255))

    # Pill badges row
    badges = [
        ("Android 8.0+", "#2ECC71"),
        ("Jetpack Compose", "#68D391"),
        ("Kotlin 2.0", "#9AE6B4"),
        ("2.8 MB APK", "#48BB78"),
        ("In-App Auto-Update", "#38A169"),
        ("OLED Dark Mode", "#2F855A"),
        ("105 Tests Passing", "#48BB78"),
    ]

    badge_font = get_font(FONT_BOLD, 13)
    pill_padding_x = 14
    pill_h = 32
    pill_gap = 12
    pill_widths = [int(badge_font.getlength(text)) + pill_padding_x * 2 for text, _ in badges]
    total_row_w = sum(pill_widths) + pill_gap * (len(badges) - 1)
    
    start_x = (W - total_row_w) // 2
    cur_x = start_x
    pill_y = 325

    for (text, accent), pw in zip(badges, pill_widths):
        draw.rounded_rectangle(
            [cur_x, pill_y, cur_x + pw, pill_y + pill_h],
            radius=16,
            fill=(15, 38, 26, 230),
            outline=(46, 204, 113, 160),
            width=1
        )
        tw = badge_font.getlength(text)
        draw.text((cur_x + (pw - tw) // 2, pill_y + 7), text, font=badge_font, fill=(240, 250, 245, 255))
        cur_x += pw + pill_gap

    draw.line([(0, H - 2), (W, H - 2)], fill=(46, 204, 113, 100), width=2)

    out_path = os.path.join(DOCS_IMG_DIR, "catchit_hero_banner.png")
    im.save(out_path, "PNG", optimize=True)
    print("Saved hero banner:", out_path)

# ==========================================
# 2. FEATURE SHOWCASE (1600 x 940)
# ==========================================
def create_mockup_device(img_path, width, height, radius=24):
    src = Image.open(img_path).convert("RGBA")
    
    inner_w = width - 16
    inner_h = height - 16
    
    scale = max(inner_w / src.width, inner_h / src.height)
    sw = int(src.width * scale)
    sh = int(src.height * scale)
    src_scaled = src.resize((sw, sh), Image.Resampling.LANCZOS)
    
    crop_x = (sw - inner_w) // 2
    crop_y = 0
    screen_crop = src_scaled.crop((crop_x, crop_y, crop_x + inner_w, crop_y + inner_h))
    
    mask = Image.new("L", (inner_w, inner_h), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle([0, 0, inner_w, inner_h], radius=radius - 4, fill=255)
    
    device = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ddraw = ImageDraw.Draw(device)
    
    ddraw.rounded_rectangle([0, 0, width - 1, height - 1], radius=radius, fill=(18, 24, 20, 255), outline=(46, 204, 113, 180), width=2)
    device.paste(screen_crop, (8, 8), mask)
    
    refl = Image.new("RGBA", (inner_w, inner_h), (0, 0, 0, 0))
    rdraw = ImageDraw.Draw(refl)
    rdraw.polygon([(0, 0), (inner_w, 0), (inner_w, 140), (0, 260)], fill=(255, 255, 255, 12))
    refl_masked = Image.composite(refl, Image.new("RGBA", (inner_w, inner_h), (0, 0, 0, 0)), mask)
    device.paste(refl_masked, (8, 8), refl_masked)
    
    return device

def make_feature_showcase():
    W, H = 1600, 940
    im = Image.new("RGBA", (W, H), (10, 18, 14, 255))
    draw = ImageDraw.Draw(im)

    for y in range(H):
        ratio = y / float(H)
        r = int(10 + (16 - 10) * ratio)
        g = int(18 + (36 - 18) * ratio)
        b = int(14 + (24 - 14) * ratio)
        draw.line([(0, y), (W, y)], fill=(r, g, b, 255))

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    for cx in [230, 610, 990, 1370]:
        gdraw.ellipse([cx - 240, 480 - 240, cx + 240, 480 + 240], fill=(46, 204, 113, 20))
    glow = glow.filter(ImageFilter.GaussianBlur(50))
    im = Image.alpha_composite(im, glow)
    draw = ImageDraw.Draw(im)

    title_font = get_font(FONT_BOLD, 36)
    sub_font = get_font(FONT_REG, 20)
    
    title = "Engineered for Malaysian Commuters"
    tw = title_font.getlength(title)
    draw.text(((W - tw) // 2, 40), title, font=title_font, fill=(255, 255, 255, 255))

    sub = "Real-Time GPS · Smart Offline Catalog · 2-Stage Departure Alarms · Instant OLED Dark Mode"
    sw = sub_font.getlength(sub)
    draw.text(((W - sw) // 2, 88), sub, font=sub_font, fill=(160, 185, 172, 255))

    todak_path = os.path.join(ART_DIR, "todak_resolved.png")
    if not os.path.exists(todak_path):
        todak_path = os.path.join(STORE_SCREENSHOTS, "03_route_live_map.png")

    screens = [
        (os.path.join(STORE_SCREENSHOTS, "01_bus_home.png"), "1. Live Bus Radar", "Instant next arrival ETAs & live status"),
        (todak_path, "2. Destination Search", "Find nearest stops by landmarks & walking distance"),
        (os.path.join(STORE_SCREENSHOTS, "07_alarm_setup.png"), "3. 2-Stage Alarms", "Wakeup warning & departure arrival ring"),
        (os.path.join(ART_DIR, "home_dark_screen.png"), "4. OLED Dark Mode", "Pitch-black UI crafted for evening commutes")
    ]

    card_w = 340
    card_h = 660
    gap = 46
    total_w = len(screens) * card_w + (len(screens) - 1) * gap
    start_x = (W - total_w) // 2
    top_y = 150

    card_title_font = get_font(FONT_BOLD, 17)
    card_sub_font = get_font(FONT_REG, 13)

    for i, (path, card_title, card_desc) in enumerate(screens):
        x = start_x + i * (card_w + gap)
        device = create_mockup_device(path, card_w, card_h)
        im.paste(device, (x, top_y), device)

        label_y = top_y + card_h + 18
        ltw = card_title_font.getlength(card_title)
        draw.text((x + (card_w - ltw) // 2, label_y), card_title, font=card_title_font, fill=(46, 204, 113, 255))
        
        ldw = card_sub_font.getlength(card_desc)
        draw.text((x + (card_w - ldw) // 2, label_y + 24), card_desc, font=card_sub_font, fill=(180, 200, 190, 255))

    out_path = os.path.join(DOCS_IMG_DIR, "catchit_showcase.png")
    im.save(out_path, "PNG", optimize=True)
    print("Saved feature showcase:", out_path)

# ==========================================
# 3. THEME COMPARISON (1200 x 700)
# ==========================================
def make_theme_comparison():
    W, H = 1200, 700
    im = Image.new("RGBA", (W, H), (12, 18, 15, 255))
    draw = ImageDraw.Draw(im)

    for y in range(H):
        ratio = y / float(H)
        r = int(10 + (16 - 10) * ratio)
        g = int(18 + (34 - 18) * ratio)
        b = int(14 + (24 - 14) * ratio)
        draw.line([(0, y), (W, y)], fill=(r, g, b, 255))

    t_font = get_font(FONT_BOLD, 30)
    s_font = get_font(FONT_REG, 17)
    
    title = "Adaptive Design: Light Mode vs OLED Dark Mode"
    tw = t_font.getlength(title)
    draw.text(((W - tw) // 2, 32), title, font=t_font, fill=(255, 255, 255, 255))

    sub = "Seamless manual toggle in Settings with instant real-time theme switching"
    sw = s_font.getlength(sub)
    draw.text(((W - sw) // 2, 72), sub, font=s_font, fill=(160, 185, 172, 255))

    card_w = 280
    card_h = 500
    gap = 120
    total_w = card_w * 2 + gap
    start_x = (W - total_w) // 2
    top_y = 115

    # Light device
    light_path = os.path.join(STORE_SCREENSHOTS, "01_bus_home.png")
    light_dev = create_mockup_device(light_path, card_w, card_h, radius=20)
    im.paste(light_dev, (start_x, top_y), light_dev)

    # Dark device
    dark_path = os.path.join(ART_DIR, "home_dark_screen.png")
    dark_dev = create_mockup_device(dark_path, card_w, card_h, radius=20)
    im.paste(dark_dev, (start_x + card_w + gap, top_y), dark_dev)

    # Middle badge "VS"
    vs_cx = W // 2
    vs_cy = top_y + card_h // 2
    draw.ellipse([vs_cx - 28, vs_cy - 28, vs_cx + 28, vs_cy + 28], fill=(22, 54, 36, 255), outline=(46, 204, 113, 255), width=2)
    vs_font = get_font(FONT_BOLD, 18)
    vsw = vs_font.getlength("VS")
    draw.text((vs_cx - vsw // 2, vs_cy - 12), "VS", font=vs_font, fill=(46, 204, 113, 255))

    # Captions below devices
    lbl_font = get_font(FONT_BOLD, 18)
    sub_lbl_font = get_font(FONT_REG, 14)

    lx = start_x + card_w // 2
    l_txt = "Daylight Clarity (Light)"
    l_sub = "High contrast in direct sunlight"
    draw.text((lx - lbl_font.getlength(l_txt) // 2, top_y + card_h + 14), l_txt, font=lbl_font, fill=(240, 246, 250, 255))
    draw.text((lx - sub_lbl_font.getlength(l_sub) // 2, top_y + card_h + 38), l_sub, font=sub_lbl_font, fill=(160, 180, 170, 255))

    dx = start_x + card_w + gap + card_w // 2
    d_txt = "Pure Pitch Black (OLED Dark)"
    d_sub = "#000000 true black, zero eye strain"
    draw.text((dx - lbl_font.getlength(d_txt) // 2, top_y + card_h + 14), d_txt, font=lbl_font, fill=(46, 204, 113, 255))
    draw.text((dx - sub_lbl_font.getlength(d_sub) // 2, top_y + card_h + 38), d_sub, font=sub_lbl_font, fill=(160, 180, 170, 255))

    out_path = os.path.join(DOCS_IMG_DIR, "catchit_theme_comparison.png")
    im.save(out_path, "PNG", optimize=True)
    print("Saved theme comparison:", out_path)

if __name__ == "__main__":
    make_hero_banner()
    make_feature_showcase()
    make_theme_comparison()
