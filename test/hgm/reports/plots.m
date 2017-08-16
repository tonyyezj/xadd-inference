% taking the current path:
[current_path,name,ext] = fileparts(mfilename('fullpath'));
current_path = [current_path, '\'];

% parsing info file:


vis = 'on';
figure('visible', vis);
hold on;

data1 = load([current_path, 'trueskill-car-db.txt']);
plot(data1(:,1),data1(:,2), '-ko', 'LineWidth', 1);

data2 = load([current_path, 'poly-binary-noise045-samples100-trimless.txt']);
plot(data2(:,1),data2(:,2), '-bo', 'LineWidth', 2);

data3 = load([current_path, 'polytope-noise045-trimless-car-db1.txt']);
plot(data3(:,1),data3(:,2), '-ro', 'LineWidth', 2);

hleg1 = legend('trueSkill','Binarized poly', 'real-valued poly');
xlabel('no. constraints')
ylabel('loss')

hold off;
%xlabel('X', 'FontSize', font_size);
%ylabel('Y', 'FontSize', font_size);
%title(fig_title);

%generate pdf:

eps_file = [current_path 'trueskill-vs-poly-car-db.eps'];
print('-depsc', eps_file);
%system(['sh convert_images.sh ' f]);
disp(eps_file);

system(['epstopdf ', eps_file]);


%print('-depsc', [current_path 'scatter2D.eps']);
%disp([current_path 'scatter2D']);
%system(['epstopdf ' current_path 'scatter2D.eps']);

%................................

%[data_files, titles, dims] = textread([current_path, 'scatter2D.txt'], '%s %s %f');

%for i=1:length(data_files)
  %if dims(i)== 1
      %one_dim_scatter_plot([current_path, data_files{i}], titles{i});
 % end %if

 % if dims(i) == 2
 %     two_dim_plot([current_path, data_files{i}], titles{i});
 % end %if
%end %for
