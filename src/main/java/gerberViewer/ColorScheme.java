package gerberViewer;


import java.awt.Color;

public class ColorScheme {
    Color backgroundColor = Color.LIGHT_GRAY;
    Color maskColor = new Color(29, 1, 43, 230);
    Color substrateColor = Color.GRAY;
    Color copperColor = new Color(232, 153, 97);
    Color legendColor = new Color(249, 247, 250);
    Color reticleColor = new Color(255, 255, 255, 64);
    
    ColorScheme(ColorScheme colorScheme) {
        this.backgroundColor = colorScheme.backgroundColor;
        this.maskColor = colorScheme.maskColor;
        this.substrateColor = colorScheme.substrateColor;
        this.copperColor = colorScheme.copperColor;
        this.legendColor = colorScheme.legendColor;
        this.reticleColor = colorScheme.reticleColor;
    }
    
    ColorScheme(String name) {
        switch (name) {
            case "Green":
                backgroundColor = Color.LIGHT_GRAY;
                maskColor = new Color(5, 54, 3, 230);
                substrateColor = Color.GRAY;
                copperColor = new Color(232, 153, 97);
                legendColor = new Color(249, 247, 250);
                reticleColor = new Color(255, 255, 255, 64);
                break;
            case "Purple":
                backgroundColor = Color.LIGHT_GRAY;
                maskColor = new Color(29, 1, 43, 230);
                substrateColor = Color.GRAY;
                copperColor = new Color(232, 153, 97);
                legendColor = new Color(249, 247, 250);
                reticleColor = new Color(255, 255, 255, 64);
                break;
            case "Red":
                backgroundColor = Color.LIGHT_GRAY;
                maskColor = new Color(153, 8, 8, 230);
                substrateColor = Color.GRAY;
                copperColor = new Color(232, 153, 97);
                legendColor = new Color(249, 247, 250);
                reticleColor = new Color(255, 255, 255, 64);
                break;
            case "Yellow":
                backgroundColor = Color.DARK_GRAY;
                maskColor = new Color(245, 229, 12, 230);
                substrateColor = Color.GRAY;
                copperColor = new Color(232, 153, 97);
                legendColor = new Color(5, 5, 5);
                reticleColor = new Color(25, 25, 25, 64);
                break;
            case "Blue":
                backgroundColor = Color.LIGHT_GRAY;
                maskColor = new Color(4, 11, 79, 230);
                substrateColor = Color.GRAY;
                copperColor = new Color(232, 153, 97);
                legendColor = new Color(249, 247, 250);
                reticleColor = new Color(255, 255, 255, 64);
                break;
            case "White":
                backgroundColor = Color.DARK_GRAY;
                maskColor = new Color(250, 250, 250, 230);
                substrateColor = Color.GRAY;
                copperColor = new Color(232, 153, 97);
                legendColor = new Color(5, 5, 5);
                reticleColor = new Color(25, 25, 25, 64);
                break;
            case "Black":
                backgroundColor = Color.LIGHT_GRAY;
                maskColor = new Color(0, 0, 0, 230);
                substrateColor = Color.GRAY;
                copperColor = new Color(232, 153, 97);
                legendColor = new Color(249, 247, 250);
                reticleColor = new Color(255, 255, 255, 64);
                break;
            default:
        }
    }
}
